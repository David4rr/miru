use miru_core::proxy::parse_hls_variants;

#[test]
fn test_parse_standard_master_playlist() {
    let manifest = r#"#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.64001f,mp4a.40.2"
360p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=842x480,CODECS="avc1.64001f,mp4a.40.2"
480p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,CODECS="avc1.640028,mp4a.40.2"
720p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
1080p/index.m3u8
"#;
    let variants = parse_hls_variants(manifest);
    assert_eq!(variants.len(), 4);
    // Highest resolution first due to sorting
    assert_eq!(variants[0].resolution, "1080p");
    assert_eq!(variants[0].width, 1920);
    assert_eq!(variants[0].height, 1080);
    assert_eq!(variants[0].bandwidth, 5000000);
    assert_eq!(variants[0].url, "1080p/index.m3u8");

    assert_eq!(variants[1].resolution, "720p");
    assert_eq!(variants[2].resolution, "480p");
    assert_eq!(variants[3].resolution, "360p");
}

#[test]
fn test_audio_only_and_edge_cases() {
    let manifest = r#"#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
audio_only.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3200000,RESOLUTION=1920x800,CODECS="avc1.640028"
ultrawide_800p.m3u8
"#;
    let variants = parse_hls_variants(manifest);
    // Audio only has no RESOLUTION, so it should be omitted from video variants
    assert_eq!(variants.len(), 1);
    assert_eq!(variants[0].resolution, "800p");
    assert_eq!(variants[0].width, 1920);
    assert_eq!(variants[0].height, 800);
    assert_eq!(variants[0].bandwidth, 3200000);
    assert_eq!(variants[0].url, "ultrawide_800p.m3u8");
}

#[test]
fn test_empty_manifest() {
    let variants = parse_hls_variants("");
    assert!(variants.is_empty());
}
