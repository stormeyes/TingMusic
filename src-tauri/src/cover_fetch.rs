use anyhow::Result;
use base64::Engine;
use serde::Deserialize;
use std::path::Path;
use std::time::Duration;

#[derive(Deserialize)]
struct ItunesResponse {
    results: Vec<ItunesResult>,
}

#[derive(Deserialize)]
struct ItunesResult {
    #[serde(rename = "artworkUrl100")]
    artwork_url_100: Option<String>,
}

pub async fn fetch_cover(
    title: &str,
    artist: &str,
    cache_dir: &Path,
    track_id: &str,
) -> Result<Option<String>> {
    let cache_path = cache_dir.join(format!("{track_id}.jpg"));
    if cache_path.is_file() {
        if let Ok(bytes) = std::fs::read(&cache_path) {
            if !bytes.is_empty() {
                return Ok(Some(to_jpeg_data_url(&bytes)));
            }
        }
    }

    let title_t = title.trim();
    if title_t.is_empty() {
        return Ok(None);
    }
    let term = if artist.trim().is_empty() || artist.trim() == "Unknown" {
        title_t.to_string()
    } else {
        format!("{} {}", title_t, artist.trim())
    };

    let url = format!(
        "https://itunes.apple.com/search?term={}&media=music&limit=1",
        urlencoding::encode(&term)
    );
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .user_agent("TingMusic/0.1")
        .build()?;

    let resp = client.get(&url).send().await?;
    if !resp.status().is_success() {
        return Ok(None);
    }
    let body: ItunesResponse = resp.json().await?;
    let Some(art_url) = body.results.into_iter().find_map(|r| r.artwork_url_100) else {
        return Ok(None);
    };

    // iTunes returns 100x100bb.jpg; bumping to 600x600bb.jpg gives a usable size.
    let big_url = art_url.replace("100x100bb", "600x600bb");
    let img_resp = client.get(&big_url).send().await?;
    if !img_resp.status().is_success() {
        return Ok(None);
    }
    let bytes = img_resp.bytes().await?;
    if bytes.is_empty() {
        return Ok(None);
    }

    if let Some(parent) = cache_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = std::fs::write(&cache_path, &bytes);

    Ok(Some(to_jpeg_data_url(&bytes)))
}

fn to_jpeg_data_url(bytes: &[u8]) -> String {
    let b64 = base64::engine::general_purpose::STANDARD.encode(bytes);
    format!("data:image/jpeg;base64,{b64}")
}
