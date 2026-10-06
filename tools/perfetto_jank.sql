-- Top janky app frames (FrameTimeline actuals worse than 25ms).
SELECT ts / 1e6 AS start_ms, dur / 1e6 AS dur_ms, jank_type, layer_name
FROM actual_frame_timeline_slice
WHERE dur > 25e6
ORDER BY dur DESC
LIMIT 30;
