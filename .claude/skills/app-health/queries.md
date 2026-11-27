# App health queries

All tables are in project `sellsnap-6e85c`. `E` stands for
`` `sellsnap-6e85c.analytics_538260747.humans_events` ``; the window is the last 14 days:
`PARSE_DATE('%Y%m%d', event_date) >= DATE_SUB(CURRENT_DATE(), INTERVAL 14 DAY)`.

## Crashes by issue

```sql
SELECT plat, issue_id, ANY_VALUE(issue_title) title, error_type,
  ANY_VALUE((SELECT SUBSTR(k.value, 1, 200) FROM UNNEST(custom_keys) k WHERE k.key = 'KotlinException')) ios_error,
  ANY_VALUE((SELECT STRING_AGG(CONCAT(e.type, ': ', SUBSTR(IFNULL(e.exception_message, ''), 1, 200)), ' | ') FROM UNNEST(exceptions) e)) android_error,
  STRING_AGG(DISTINCT application.display_version) versions, ANY_VALUE(device.model) model,
  COUNT(*) n, COUNT(DISTINCT installation_uuid) installs, DATE(MAX(event_timestamp)) last_seen
FROM (
  SELECT 'android' plat, * EXCEPT(platform) FROM `sellsnap-6e85c.firebase_crashlytics.com_sirelon_sellsnap_ANDROID_REALTIME`
  UNION ALL
  SELECT 'ios', * EXCEPT(platform) FROM `sellsnap-6e85c.firebase_crashlytics.com_sirelon_sellsnap_IOS_REALTIME`)
WHERE event_timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY)
GROUP BY 1, 2, 4 ORDER BY n DESC
```

`* EXCEPT(platform)` is required: both tables already carry a `platform` column.

## Seller table: one row per install

```sql
SELECT SUBSTR(user_pseudo_id, 1, 6) u, ANY_VALUE(platform) p, ANY_VALUE(geo.country) country,
  ANY_VALUE(device.mobile_model_name) model, MAX(app_info.version) v,
  MIN(event_date) first, MAX(event_date) last, COUNT(DISTINCT event_date) days,
  COUNTIF(event_name = 'auth_started') login_start, COUNTIF(event_name = 'auth_completed') login_ok,
  COUNTIF(event_name = 'auth_abandoned') login_quit,
  COUNTIF(event_name = 'ad_generation_started') gen_start, COUNTIF(event_name = 'ad_generation_succeeded') gen_ok,
  COUNTIF(event_name = 'ad_generation_failed') gen_fail, COUNTIF(event_name = 'ad_content_copied') copied,
  COUNTIF(event_name = 'ad_publish_started') pub_start, COUNTIF(event_name = 'ad_publish_succeeded') pub_ok,
  COUNTIF(event_name = 'ad_publish_failed') pub_fail,
  COUNTIF(event_name = 'messages_conversation_opened') chats_opened, COUNTIF(event_name = 'message_sent') msg_sent,
  COUNTIF(event_name = 'draft_opened') drafts
FROM E WHERE <window>
GROUP BY user_pseudo_id ORDER BY last DESC
```

## Daily activity

```sql
SELECT event_date, COUNT(DISTINCT user_pseudo_id) active,
  COUNTIF(event_name = 'ad_generation_started') gen_start, COUNTIF(event_name = 'ad_publish_succeeded') published
FROM E WHERE <window> GROUP BY 1 ORDER BY 1
```

## Failure reasons

```sql
SELECT event_name,
  (SELECT COALESCE(value.string_value, CAST(value.int_value AS STRING)) FROM UNNEST(event_params) WHERE key IN ('reason', 'screen') LIMIT 1) reason,
  COUNT(*) n, COUNT(DISTINCT user_pseudo_id) installs
FROM E WHERE <window>
  AND event_name IN ('ad_generation_failed', 'ad_publish_failed', 'error_shown', 'auth_abandoned', 'account_add_failed')
GROUP BY 1, 2 ORDER BY 1, n DESC
```

## Version mix

```sql
SELECT platform, app_info.version, COUNT(DISTINCT user_pseudo_id) installs
FROM E WHERE <window> GROUP BY 1, 2 ORDER BY 1, 2 DESC
```

## Event inventory

Run when a new feature shipped, to find events the queries above do not count yet:

```sql
SELECT event_name, COUNT(*) n, COUNT(DISTINCT user_pseudo_id) installs
FROM E WHERE <window> AND event_name NOT IN ('screen_view', 'user_engagement')
GROUP BY 1 ORDER BY n DESC
```

Event meanings and params: the KDoc in `composeApp/src/commonMain/kotlin/com/sirelon/sellsnap/analytics/AnalyticsEvents.kt`.
