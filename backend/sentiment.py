import nltk
from textblob import TextBlob
from sqlalchemy.orm import Session
from database import SessionLocal, NewsArticle

# Automatically download NLTK data required for TextBlob if not already available
try:
    nltk.download('punkt', quiet=True)
except Exception:
    pass

# Custom financial lexicon for the Pakistani economic news context
# Maps specific keywords to sentiment boosts/penalties
FINANCIAL_KEYWORDS = {
    # Positive Economic Indicators
    "record high": 0.4,
    "rally": 0.4,
    "gains": 0.3,
    "surges": 0.3,
    "growth": 0.3,
    "cut": 0.25, # e.g. "SBP cuts interest rate" -> Positive for investments
    "cuts": 0.25,
    "reduction": 0.2,
    "drops": 0.2, # e.g. "inflation drops" -> Positive
    "falling": 0.2,
    "imf tranche": 0.3,
    "IMF approves": 0.3,
    "strengthens": 0.25,
    "halal investment": 0.3,
    "simplifies": 0.2,
    "crosses milestone": 0.3,
    "increase": 0.1,
    
    # Negative Economic Indicators
    "sheds": -0.4,
    "drops": -0.2, # default drop (if not inflation)
    "plunges": -0.4,
    "hike": -0.3, # e.g. "tax hike", "fuel hike" -> Negative
    "hikes": -0.3,
    "rise": -0.1,  # e.g. "inflation rises"
    "creeps up": -0.2,
    "taxation": -0.15,
    "tariff": -0.2,
    "tariffs": -0.2,
    "deficit": -0.35,
    "deficits": -0.35,
    "debt": -0.25,
    "penalizes": -0.3,
    "violating": -0.35,
    "fines": -0.25,
    "instability": -0.3,
    "uncertainty": -0.3,
    "sell-off": -0.35,
    "drop": -0.2
}

def analyze_sentiment(text):
    """
    Computes a sentiment score between -1.0 (very negative) and +1.0 (very positive)
    using a hybrid of TextBlob polarity and a custom financial lexicon.
    """
    if not text:
        return 0.0
        
    # Get base polarity from TextBlob (-1.0 to +1.0)
    blob = TextBlob(text)
    base_score = blob.sentiment.polarity
    
    # Apply custom financial lexicon adjustments
    adjusted_score = base_score
    words = text.lower().split()
    
    # Track word adjustments
    for keyword, adjustment in FINANCIAL_KEYWORDS.items():
        if keyword in text.lower():
            # Special compound rules:
            # "inflation drops" or "inflation falling" is positive, but default "drops" is negative
            if keyword in ["drops", "falling", "cut", "cuts"] and "inflation" in text.lower():
                adjusted_score += abs(adjustment) # Convert negative adjustment to positive boost
            elif keyword in ["rise", "hike", "hikes"] and "inflation" in text.lower():
                adjusted_score -= abs(adjustment) # Ensure negative impact for inflation rising
            else:
                adjusted_score += adjustment
                
    # Normalize score between -1.0 and +1.0
    normalized_score = max(-1.0, min(1.0, adjusted_score))
    return round(normalized_score, 2)

def update_news_sentiments():
    """
    Fetches all news articles in the database, analyzes their titles/content,
    and updates their sentiment scores.
    """
    db = SessionLocal()
    try:
        articles = db.query(NewsArticle).all()
        print(f"Analyzing sentiments for {len(articles)} news articles...")
        
        updated_count = 0
        for article in articles:
            # We combine title and content for a more context-rich analysis
            full_text = f"{article.title}. {article.content or ''}"
            score = analyze_sentiment(full_text)
            
            # Update only if score changed
            if article.sentiment_score != score:
                article.sentiment_score = score
                updated_count += 1
                
        db.commit()
        print(f"Sentiment analysis complete: Updated {updated_count} articles.")
        
    except Exception as e:
        print(f"Error during sentiment updates: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    update_news_sentiments()
Xx` 