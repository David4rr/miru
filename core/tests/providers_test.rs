use miru_core::api::providers;
use miru_core::db::{Database, WatchHistoryItem};

#[tokio::test]
async fn test_provider_registry_and_servers() {
    let hianime = providers::get_provider("hianime").expect("HiAnime should exist");
    assert_eq!(hianime.id(), "hianime");
    assert!(hianime.has_sub());
    assert!(hianime.has_dub());

    let servers_sub = hianime.get_servers("one-piece", 1, "sub").await.unwrap();
    assert!(!servers_sub.is_empty());
    assert!(servers_sub.iter().any(|s| s.server_id == "megacloud"));

    let servers_dub = hianime.get_servers("one-piece", 1, "dub").await.unwrap();
    assert!(!servers_dub.is_empty());

    let anichan = providers::get_provider("anichan").expect("AniChan should exist");
    assert_eq!(anichan.id(), "anichan");
    assert!(anichan.has_sub());
    assert!(!anichan.has_dub());
    assert!(anichan.get_servers("naruto", 1, "dub").await.is_err());
    let anichan_servers = anichan.get_servers("naruto", 1, "sub").await.unwrap();
    assert!(anichan_servers.iter().any(|s| s.server_id == "pixeldrain"));

    let otakudesu = providers::get_provider("otakudesu").expect("Otakudesu should exist");
    assert_eq!(otakudesu.id(), "otakudesu");
    assert!(otakudesu.has_sub());
    assert!(!otakudesu.has_dub());
    let otaku_servers = otakudesu.get_servers("bleach", 1, "sub").await.unwrap();
    assert!(otaku_servers.iter().any(|s| s.server_id == "desustream"));
    assert!(otaku_servers.iter().any(|s| s.server_id == "mp4load"));
    assert!(providers::get_provider("unknown_xyz").is_none());
}

#[test]
fn test_default_provider_persistence() {
    let path = std::env::temp_dir().join(format!("miru_test_prov_{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let db = Database::init(&path).unwrap();

    assert_eq!(providers::get_default_provider_id(&db), "hianime");

    providers::set_default_provider_id(&db, "anichan").unwrap();
    assert_eq!(providers::get_default_provider_id(&db), "anichan");

    assert!(providers::set_default_provider_id(&db, "fake_prov").is_err());
    assert_eq!(providers::get_default_provider_id(&db), "anichan");

    let _ = std::fs::remove_file(&path);
}

#[test]
fn test_sub_and_dub_mode_isolation_in_db() {
    let path = std::env::temp_dir().join(format!("miru_test_iso_{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let db = Database::init(&path).unwrap();

    // 1. Add SUB history
    db.add_history(&WatchHistoryItem {
        id: Some(1),
        slug: "frieren".to_string(),
        title: "Frieren".to_string(),
        episode: 5,
        mode: "sub".to_string(),
        progress: 45,
        poster: "frieren.jpg".to_string(),
        anime_type: "TV".to_string(),
        played_at: None,
    }).unwrap();

    // 2. Add DUB history for same anime
    db.add_history(&WatchHistoryItem {
        id: Some(2),
        slug: "frieren".to_string(),
        title: "Frieren".to_string(),
        episode: 1,
        mode: "dub".to_string(),
        progress: 10,
        poster: "frieren.jpg".to_string(),
        anime_type: "TV".to_string(),
        played_at: None,
    }).unwrap();

    let history = db.get_history(10).unwrap();
    assert_eq!(history.len(), 2, "SUB and DUB must coexist in watch history");

    let sub_item = history.iter().find(|h| h.mode == "sub").unwrap();
    assert_eq!(sub_item.episode, 5);
    assert_eq!(sub_item.progress, 45);

    let dub_item = history.iter().find(|h| h.mode == "dub").unwrap();
    assert_eq!(dub_item.episode, 1);
    assert_eq!(dub_item.progress, 10);

    // 3. Test watch_progress isolation
    db.save_progress("frieren", 5, Some("sub"), 80, 1400, None, None, None).unwrap();
    db.save_progress("frieren", 5, Some("dub"), 20, 1400, None, None, None).unwrap();

    let p_sub = db.get_progress("frieren", 5, Some("sub")).unwrap().unwrap();
    assert_eq!(p_sub.progress, 80);

    let p_dub = db.get_progress("frieren", 5, Some("dub")).unwrap().unwrap();
    assert_eq!(p_dub.progress, 20);

    let _ = std::fs::remove_file(&path);
}

#[test]
fn test_otakudesu_extractor_unpack() {
    use miru_core::api::providers::otakudesu_extractor::unpack_packer;
    let sample = r#"eval(function(p,a,c,k,e,d){return p}('0 1=2;',3,3,'var|stream|https'.split('|')))"#;
    let unpacked = unpack_packer(sample);
    assert_eq!(unpacked, "var stream=https;");
}

#[test]
fn test_otakudesu_parsers() {
    use miru_core::api::providers::otakudesu::{parse_search_results, parse_mirror_embeds, find_episode_link};
    let search_html = r#"
        <ul class="chivsrc">
            <li style="list-style:none;">
                <img src="https://otakudesu.blog/wp-content/uploads/boruto.jpg" />
                <h2><a href="https://otakudesu.blog/anime/borot-sub-indo/">Boruto: Naruto Next Generations</a></h2>
            </li>
        </ul>
    "#;
    let results = parse_search_results(search_html);
    assert_eq!(results.len(), 1);
    assert_eq!(results[0].id, "borot-sub-indo");
    assert_eq!(results[0].title, "Boruto: Naruto Next Generations");

    let anime_page = r#"
        <div class="episodelist">
            <a href="https://otakudesu.blog/episode/pembatas-episode-1-900/">Pembatas Episode 1</a>
            <a href="https://otakudesu.blog/episode/btr-ng-episode-293-sub-indo/">Boruto Episode 293 Sub Indo</a>
        </div>
    "#;
    let ep_link = find_episode_link(anime_page, 293);
    assert_eq!(ep_link, Some("https://otakudesu.blog/episode/btr-ng-episode-293-sub-indo/".to_string()));
    assert_eq!(find_episode_link(anime_page, 1), None);

    let ep_page = r#"
        <div class="responsive-embed-stream">
            <iframe src="https://desustream.net/dstream/moedesu/new/?id=abc"></iframe>
        </div>
        <div class="mirrorstream">
            <ul class="m360p">
                <li><a data-content="eyJpZCI6MTIzfQ==">mp4load</a></li>
            </ul>
        </div>
    "#;
    let mirrors = parse_mirror_embeds(ep_page);
    assert_eq!(mirrors.len(), 2);
    assert_eq!(mirrors[0].0, "desustream");
    assert_eq!(mirrors[0].1, "https://desustream.net/dstream/moedesu/new/?id=abc");
    assert_eq!(mirrors[1].0, "mp4load");
}

#[tokio::test]
async fn test_live_otakudesu_resolve() {
    let otakudesu = providers::get_provider("otakudesu").expect("Otakudesu should exist");
    let res = otakudesu.resolve_episode("one-piece", 1, "sub", None, None, Some("One Piece")).await;
    if let Ok(stream) = res {
        assert!(!stream.master.is_empty(), "Master stream should not be empty");
        assert!(stream.master.starts_with("http"), "Master stream should be a valid URL");
    }
}
