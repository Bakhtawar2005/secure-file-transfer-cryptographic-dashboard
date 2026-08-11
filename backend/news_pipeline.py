import datetime
import xml.etree.ElementTree as ET
import html
import re
import pandas as pd
from textblob import TextBlob
import requests
from database import SessionLocal, init_db, NewsArticle

# ====================================================
# 1. DEFINE SOURCES
# ====================================================
NEWS_SOURCES = {
    "Business Recorder": "https://www.brecorder.com/feeds/latest-news/",
    "Dawn Business": "https://www.dawn.com/feeds/business/",
    "Express Tribune Business": "https://tribune.com.pk/feed/business",
    "The News Business": "https://www.thenews.com.pk/rss/1/2",
    "Profit by Pakistan Today": "https://profit.pakistantoday.com.pk/feed/",
    "Mettis Global": "https://mettisglobal.news/feed/"
}

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

# ====================================================
# 2. DATA CLEANING & NORMALISATION FUNCTIONS
# ====================================================
def clean_text(raw_html):
    if not raw_html:
        return ""
    clean_r = re.compile('<.*?>')
    text = re.sub(clean_r, '', raw_html)
    text = html.unescape(text)
    text = " ".join(text.split())
    return text.strip()

def normalize_date(date_str):
    if not date_str:
        return datetime.datetime.utcnow()
    
    for fmt in ("%a, %d %b %Y %H:%M:%S %Z", "%a, %d %b %Y %H:%M:%S %z", "%d %b %Y %H:%M:%S %z", "%Y-%m-%d %H:%M:%S"):
        try:
            clean_str = date_str.strip()
            if clean_str.endswith(" GMT"):
                clean_str = clean_str[:-4] + " +0000"
            return datetime.datetime.strptime(clean_str, fmt)
        except ValueError:
            continue
    return datetime.datetime.utcnow()

# ====================================================
# 3. FEATURE EXTRACTION FUNCTIONS
# ====================================================
def detect_pakistan_entities(text):
    entities = []
    text_lower = text.lower()
    if "state bank" in text_lower or "sbp" in text_lower or "monetary policy" in text_lower:
        entities.append("State Bank of Pakistan (SBP)")
    if "stock exchange" in text_lower or "psx" in text_lower or "kse" in text_lower:
        entities.append("Pakistan Stock Exchange (PSX)")
    if "federal board of revenue" in text_lower or "fbr" in text_lower or "tax dept" in text_lower:
        entities.append("Federal Board of Revenue (FBR)")
    if "secp" in text_lower or "securities and exchange" in text_lower:
        entities.append("SECP (Securities Regulator)")
    if "imf" in text_lower or "monetary fund" in text_lower:
        entities.append("International Monetary Fund (IMF)")
    if "nepra" in text_lower or "power regulatory" in text_lower:
        entities.append("NEPRA (Energy Regulator)")
    if "ogra" in text_lower or "oil and gas regulatory" in text_lower:
        entities.append("OGRA (Gas & Oil Regulator)")
        
    return ", ".join(entities) if entities else "None"

def detect_sectors(text):
    sectors = []
    text_lower = text.lower()
    if any(k in text_lower for k in ["bank", "banking", "mcb", "hbl", "meezan", "ubl", "kibor"]):
        sectors.append("Banking & Finance")
    if any(k in text_lower for k in ["power", "electricity", "gas", "nepra", "ogra", "petrol", "oil", "fuel", "hubco", "ogdc", "ppl"]):
        sectors.append("Energy & Power")
    if any(k in text_lower for k in ["textile", "cotton", "yarn", "garment", "export"]):
        sectors.append("Textiles")
    if any(k in text_lower for k in ["cement", "lucky", "dg khan", "fauji"]):
        sectors.append("Construction & Cement")
    if any(k in text_lower for k in ["fertilizer", "engro", "ffc"]):
        sectors.append("Agriculture & Fertilizer")
    if any(k in text_lower for k in ["tech", "systems", "trg", "software", "information technology"]):
        sectors.append("Technology")
    if any(k in text_lower for k in ["auto", "honda", "toyota", "suzuki", "car"]):
        sectors.append("Automotive")
        
    return ", ".join(sectors) if sectors else "Macro-economy"

def classify_event(text):
    text_lower = text.lower()
    if "budget" in text_lower or "fiscal year" in text_lower:
        return "Budget News"
    if any(k in text_lower for k in ["tax", "gst", "duty", "duties", "fbr", "taxation"]):
        return "Tax Changes"
    if any(k in text_lower for k in ["interest rate", "kibor", "sbp", "monetary policy", "policy rate"]):
        return "SBP Policy News"
    if any(k in text_lower for k in ["psx", "kse", "dividend", "earnings", "listed", "stock"]):
        return "PSX News"
    if any(k in text_lower for k in ["inflation", "cpi", "spi", "prices", "costly"]):
        return "Inflation"
    if any(k in text_lower for k in ["rupee", "pkr", "exchange rate", "dollar", "usd"]):
        return "PKR Exchange News"
    if any(k in text_lower for k in ["import", "export", "trade", "tariff"]):
        return "Import/Export News"
    if any(k in text_lower for k in ["power tariff", "electricity price", "petrol price", "gas price", "lng"]):
        return "Energy News"
    if any(k in text_lower for k in ["political", "election", "government", "minister", "parliament"]):
        return "Political News"
        
    return "General Economic News"

# ====================================================
# 4. MAIN PIPELINE EXECUTION
# ====================================================
def run_pipeline():
    print("Initializing News Pipeline Database...")
    init_db()
    db = SessionLocal()
    
    all_processed_articles = []
    articles_added = 0
    
    print("--- Phase 1: Data Collection & Cleaning ---")
    for source_name, feed_url in NEWS_SOURCES.items():
        try:
            print(f"Connecting to {source_name} RSS feed...")
            response = requests.get(feed_url, headers=HEADERS, timeout=10)
            if response.status_code != 200:
                print(f"Failed to fetch {source_name}. Status: {response.status_code}")
                continue
                
            root = ET.fromstring(response.content)
            channel = root.find("channel")
            if channel is None:
                continue
                
            items = channel.findall("item")
            print(f"Scraped {len(items)} raw articles from {source_name}.")
            
            for item in items:
                raw_title = item.find("title").text if item.find("title") is not None else ""
                link = item.find("link").text if item.find("link") is not None else ""
                raw_description = item.find("description").text if item.find("description") is not None else ""
                pub_date_str = item.find("pubDate").text if item.find("pubDate") is not None else ""
                
                # Extract raw XML response for debugging
                raw_resp_str = ET.tostring(item, encoding='utf-8').decode('utf-8')
                
                if not raw_title or not link:
                    continue
                    
                # ----------------- CLEANING & NORMALISATION -----------------
                title = clean_text(raw_title)
                description = clean_text(raw_description)
                full_text = f"{title}. {description}"
                published_time = normalize_date(pub_date_str)
                ingestion_time = datetime.datetime.utcnow()
                
                # ----------------- FEATURE EXTRACTION -----------------
                # Sentiment Score
                blob = TextBlob(full_text)
                sentiment_score = round(blob.sentiment.polarity, 2)
                
                # Named Entities, Sectors, and Events
                entities = detect_pakistan_entities(full_text)
                sectors = detect_sectors(full_text)
                event_type = classify_event(full_text)
                
                # Calculate Importance & Confidence Scores
                # SBP or IMF news is high importance (8-10), others are normal (5)
                importance_score = 9.0 if ("sbp" in full_text.lower() or "imf" in full_text.lower()) else 5.0
                confidence_score = 85.0 # Basic classification confidence metric
                
                # Keywords
                kw_list = [event_type, sectors]
                if entities != "None":
                    kw_list.append(entities)
                keywords = ", ".join(kw_list)
                
                # Related Assets mapping
                assets = []
                if "meezan" in full_text.lower(): assets.append("MEBL")
                if "systems" in full_text.lower(): assets.append("SYS")
                if "hubco" in full_text.lower() or "hub power" in full_text.lower(): assets.append("HUBC")
                if "gold" in full_text.lower(): assets.append("Gold")
                if "oil" in full_text.lower(): assets.append("Crude Oil")
                related_assets = ", ".join(assets) if assets else "None"
                
                # ----------------- PREPARE FOR DATASET -----------------
                article_data = {
                    "ID": None,
                    "Source": source_name,
                    "Source Type": "RSS Feed",
                    "URL": link,
                    "Title": title,
                    "Description": description,
                    "Full Text": full_text,
                    "Published Time (UTC)": published_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "Ingestion Time (UTC)": ingestion_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "Country": "Pakistan",
                    "Region": "South Asia",
                    "Language": "English",
                    "Asset Class": "Macroeconomic" if event_type in ["Inflation", "SBP Policy News", "Budget News"] else "Equity",
                    "Market": "Pakistan Stock Exchange (PSX)",
                    "Sector": sectors,
                    "Event Type": event_type,
                    "Importance Score": importance_score,
                    "Sentiment Score": sentiment_score,
                    "Confidence Score": confidence_score,
                    "Keywords": keywords,
                    "Named Entities": entities,
                    "Related Assets": related_assets,
                    "Raw Response": raw_resp_str
                }
                all_processed_articles.append(article_data)
                
                # ----------------- DATA STORAGE (SQLite) -----------------
                existing = db.query(NewsArticle).filter(NewsArticle.url == link).first()
                if not existing:
                    new_article = NewsArticle(
                        source=source_name,
                        source_type="RSS Feed",
                        url=link,
                        title=title,
                        description=description,
                        full_text=full_text,
                        published_time=published_time,
                        ingestion_time=ingestion_time,
                        country="Pakistan",
                        region="South Asia",
                        language="English",
                        asset_class=article_data["Asset Class"],
                        market="Pakistan Stock Exchange (PSX)",
                        sector=sectors,
                        event_type=event_type,
                        importance_score=importance_score,
                        sentiment_score=sentiment_score,
                        confidence_score=confidence_score,
                        keywords=keywords,
                        named_entities=entities,
                        related_assets=related_assets,
                        raw_response=raw_resp_str,
                        content=description,
                        published_at=published_time
                    )
                    db.add(new_article)
                    articles_added += 1
                    
            db.commit()
            
        except Exception as e:
            print(f"Error processing {source_name} feed: {e}")
            continue
            
    # Save the updated table to Excel
    if all_processed_articles:
        df_excel = pd.DataFrame(all_processed_articles)
        excel_filename = "financial_news_dataset.xlsx"
        df_excel.to_excel(excel_filename, index=False, sheet_name="Pakistan Financial News")
        print(f"\nSuccess! News pipeline dataset saved to Excel: {excel_filename}")
        print(f"Total processed articles: {len(df_excel)}")
    else:
        print("No articles collected.")
        
    print(f"Database update complete: Added {articles_added} new articles to SQLite.")
    db.close()

if __name__ == "__main__":
    run_pipeline()