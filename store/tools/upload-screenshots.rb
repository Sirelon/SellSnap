#!/usr/bin/env ruby
# Uploads the generated store screenshots (store/assets/) to App Store Connect and Google Play.
#
#   ruby store/tools/upload-screenshots.rb app-store            # dry run: prints what would change
#   ruby store/tools/upload-screenshots.rb app-store --apply    # replaces the screenshot sets
#   ruby store/tools/upload-screenshots.rb play --apply [--lang=uk,pl] [--slots=phoneScreenshots]
#
# App Store: writes into the iOS version that is still editable (PREPARE_FOR_SUBMISSION and the
# other pre-review states). A version in review or on sale is locked; the script stops there.
# The API has no display type for iPhone Duo, so those images are uploaded by hand.
# Google Play: replaces phone, 7" and 10" tablet screenshots of each listing in one edit and
# commits it, which publishes the listing change.
# Run with LANG=en_US.UTF-8 (fastlane requires it).

require "spaceship"
require "supply"

ROOT = File.expand_path("../..", __dir__)
ASSETS = File.join(ROOT, "store/assets")
APPLY = ARGV.include?("--apply")
LANGS = ARGV.find { |a| a.start_with?("--lang=") }&.split("=", 2)&.last&.split(",")
# Console slot names to touch (APP_IPHONE_67, phoneScreenshots, ...); all when omitted.
SLOTS = ARGV.find { |a| a.start_with?("--slots=") }&.split("=", 2)&.last&.split(",")

def images(dir)
  Dir.glob(File.join(dir, "*.jpg")).sort
end

def app_store
  key = File.join(ROOT, "fastlane/AuthKey_G5TTXS7GV3.p8")
  Spaceship::ConnectAPI.token = Spaceship::ConnectAPI::Token.create(
    key_id: "G5TTXS7GV3", issuer_id: "8873b38c-775e-4a3e-baf5-4e2bada765a8", filepath: key,
  )
  locales = { "en" => "en-US", "uk" => "uk", "pl" => "pl", "ro" => "ro", "pt" => "pt-PT" }
  # Display type -> generated folder. APP_IPHONE_67 takes the 6.9" 1320x2868 images,
  # APP_IPAD_PRO_3GEN_129 the 13" 2752x2064 ones.
  slots = { "APP_IPHONE_67" => "iphone-6.9", "APP_IPAD_PRO_3GEN_129" => "ipad-13" }
  editable = %w[PREPARE_FOR_SUBMISSION DEVELOPER_REJECTED REJECTED METADATA_REJECTED INVALID_BINARY]

  app = Spaceship::ConnectAPI::App.find("com.sirelon.sellsnap")
  version = app.get_app_store_versions(filter: { platform: "IOS" }).first
  puts "Latest iOS version: #{version.version_string} (#{version.app_store_state})"
  unless editable.include?(version.app_store_state)
    abort "Version #{version.version_string} is #{version.app_store_state}: screenshots are locked. " \
          "Create the next version (sellsnap-release does) and run again."
  end

  version.get_app_store_version_localizations.each do |loc|
    lang = locales.key(loc.locale)
    next puts("#{loc.locale}: no generated language, left alone") unless lang
    next if LANGS && !LANGS.include?(lang)
    sets = loc.get_app_screenshot_sets
    slots.each do |type, folder|
      next if SLOTS && !SLOTS.include?(type)
      files = images(File.join(ASSETS, "app-store", folder, lang))
      next puts("#{loc.locale} #{type}: no images in #{folder}/#{lang}, left alone") if files.empty?
      set = sets.find { |s| s.screenshot_display_type == type }
      old = set&.app_screenshots&.size || 0
      puts "#{loc.locale} #{type}: replace #{old} with #{files.size} (#{folder}/#{lang})"
      next unless APPLY
      set ||= loc.create_app_screenshot_set(attributes: { screenshotDisplayType: type })
      (set.app_screenshots || []).each(&:delete!)
      files.each { |f| set.upload_screenshot(path: f, wait_for_processing: false) }
    end
  end
  puts(APPLY ? "Uploaded. Check processing in App Store Connect." : "Dry run. Add --apply to upload.")
end

def play
  Supply.config = FastlaneCore::Configuration.create(Supply::Options.available_options, {
    package_name: "com.sirelon.sellsnap", json_key: File.join(ROOT, "fastlane/google-play-key.json"),
  })
  locales = { "en" => "en-US", "uk" => "uk", "pl" => "pl-PL", "ro" => "ro", "bg" => "bg", "pt" => "pt-PT", "kk" => "kk" }
  slots = { "phoneScreenshots" => "phone", "sevenInchScreenshots" => "tablet", "tenInchScreenshots" => "tablet" }

  client = Supply::Client.make_from_config
  client.begin_edit(package_name: "com.sirelon.sellsnap")
  begin
    client.listings.each do |listing|
      lang = locales.key(listing.language)
      next puts("#{listing.language}: no generated language, left alone") unless lang
      next if LANGS && !LANGS.include?(lang)
      slots.each do |type, folder|
        next if SLOTS && !SLOTS.include?(type)
        files = images(File.join(ASSETS, "play-store", folder, lang))
        next puts("#{listing.language} #{type}: no images in #{folder}/#{lang}, left alone") if files.empty?
        old = client.fetch_images(image_type: type, language: listing.language).size
        puts "#{listing.language} #{type}: replace #{old} with #{files.size} (#{folder}/#{lang})"
        next unless APPLY
        client.clear_screenshots(image_type: type, language: listing.language)
        files.each { |f| client.upload_image(image_path: f, image_type: type, language: listing.language) }
      end
    end
    if APPLY
      client.commit_current_edit!
      puts "Committed: the listing change is live on Google Play."
    else
      client.abort_current_edit
      puts "Dry run. Add --apply to upload."
    end
  rescue StandardError
    client.abort_current_edit rescue nil
    raise
  end
end

case ARGV.first
when "app-store" then app_store
when "play" then play
else abort "usage: ruby store/tools/upload-screenshots.rb app-store|play [--apply] [--lang=uk,pl]"
end
