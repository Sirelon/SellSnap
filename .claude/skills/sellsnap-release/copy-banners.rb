#!/usr/bin/env ruby
# Copies the App Store banners (header + search results) onto a new iOS version.
#
#   ruby .claude/skills/sellsnap-release/copy-banners.rb [targetVersion]
#
# Banner placements belong to one appStoreVersion, so a new version starts without them.
# Target: the given version string, else the newest iOS version. Source: the newest other
# version that has banners. Copies per locale and skips types the target already has.
# Talks to App Store Connect through the store-banners skill's asc.rb (same auth).
require "json"
require "open3"

ASC = File.expand_path("~/.claude/skills/store-banners/asc.rb")
APP_ID = "6776001373"
BANNER_TYPES = %w[PRODUCT_PAGE_HEADER_ASSET APP_STORE_SEARCH_RESULTS_ASSET].freeze

def asc(method, path, body = nil)
  args = ["ruby", ASC, method, path]
  args << JSON.generate(body) if body
  out, err, status = Open3.capture3(*args)
  abort "#{method} #{path} failed:\n#{err}" unless status.success?
  JSON.parse(out)
end

# { locale => { "id" => localizationId, "banners" => { type => imageId } } }
def banners(version_id)
  asc("GET", "/v1/appStoreVersions/#{version_id}/appStoreVersionLocalizations?fields[appStoreVersionLocalizations]=locale")["data"]
    .to_h do |loc|
      placements = asc("GET", "/v1/appStoreVersionLocalizations/#{loc["id"]}/placements?include=image&limit=200")["data"]
      found = placements.select { |p| BANNER_TYPES.include?(p["attributes"]["placementType"]) }
                        .to_h { |p| [p["attributes"]["placementType"], p.dig("relationships", "image", "data", "id")] }
      [loc["attributes"]["locale"], { "id" => loc["id"], "banners" => found }]
    end
end

versions = asc("GET", "/v1/apps/#{APP_ID}/appStoreVersions?filter[platform]=IOS&limit=10&fields[appStoreVersions]=versionString,appStoreState")["data"]
target = ARGV[0] ? versions.find { |v| v["attributes"]["versionString"] == ARGV[0] } : versions.first
abort "version #{ARGV[0]} not found" unless target
puts "Target #{target["attributes"]["versionString"]} (#{target["attributes"]["appStoreState"]})"

source = nil
source_banners = nil
versions.reject { |v| v["id"] == target["id"] }.each do |v|
  b = banners(v["id"])
  next if b.values.all? { |l| l["banners"].empty? }
  source, source_banners = v, b
  break
end
abort "no earlier version has banners" unless source
puts "Source #{source["attributes"]["versionString"]}"

failed = false
banners(target["id"]).each do |locale, t|
  BANNER_TYPES.each do |type|
    image = source_banners.dig(locale, "banners", type)
    if image.nil?
      puts "SKIP #{locale} #{type}: source has none"
    elsif t["banners"][type]
      puts "HAVE #{locale} #{type}"
    else
      args = ["ruby", ASC, "POST", "/v1/appAssetLibraryPlacements", JSON.generate(
        data: { type: "appAssetLibraryPlacements",
                attributes: { placementType: type, placementGroup: "DEFAULT_PROFILE" },
                relationships: { image: { data: { type: "appAssetLibraryImages", id: image } },
                                 appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: t["id"] } } } }
      )]
      _, err, status = Open3.capture3(*args)
      puts status.success? ? "OK   #{locale} #{type}" : "FAIL #{locale} #{type}: #{err[/"title" : "([^"]+)"/, 1] || err}"
      failed ||= !status.success?
    end
  end
end
exit 1 if failed
