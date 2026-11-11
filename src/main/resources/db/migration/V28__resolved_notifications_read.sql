-- A waiting question's notification that has its answer asks nothing of anyone any more: it counts as read,
-- as NotificationService now marks it when the answer comes. Rows resolved before that stayed unread in the
-- bell with no way to open them.
UPDATE notification SET read_at = resolved_at
WHERE kind = 'ESCALATION_WAITING' AND resolved_at IS NOT NULL AND read_at IS NULL;
