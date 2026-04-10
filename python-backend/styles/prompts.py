"""
Style prompt templates.
Each style has:
  - name       : display name (used by /styles endpoint)
  - positive   : scene description prompt
  - negative   : style-specific negative additions
  - preview_color : hex color for Android UI preview chip
"""

STYLES: dict[str, dict] = {
    "golden_hour_rooftop": {
        "name": "Golden Hour Rooftop",
        "preview_color": "#F5A623",
        "positive": (
            "portrait photo of a person standing on a modern city rooftop at golden hour, "
            "warm sunlight, beige linen shirt, city skyline background, bokeh, "
            "professional photography, Canon 5D, 85mm lens, f/1.8"
        ),
        "negative": "cartoon, painting, anime, deformed face, ugly, blurry, low quality, different person, changed identity",
    },
    "rainy_street_cinematic": {
        "name": "Rainy Street Cinematic",
        "preview_color": "#4A5568",
        "positive": (
            "cinematic portrait of a person on neon-lit rainy street at night, "
            "holding umbrella, navy blue trench coat, rain reflections on ground, "
            "shallow depth of field, film grain, ARRI Alexa"
        ),
        "negative": "cartoon, painting, anime, deformed face, ugly, blurry, low quality, different person",
    },
    "minimal_studio_portrait": {
        "name": "Minimal Studio Portrait",
        "preview_color": "#E2E8F0",
        "positive": (
            "professional studio portrait, clean light gray seamless background, "
            "white button-up shirt, softbox lighting, catchlights in eyes, "
            "high-end fashion photography, Hasselblad medium format"
        ),
        "negative": "outdoor, textured background, harsh shadows, deformed face, different person, low quality",
    },
    "outdoor_forest_light": {
        "name": "Outdoor Forest Light",
        "preview_color": "#48BB78",
        "positive": (
            "portrait photo in lush green forest, dappled natural sunlight through trees, "
            "olive green jacket, shallow depth of field, National Geographic style photography, "
            "warm natural tones"
        ),
        "negative": "studio, artificial light, cartoon, deformed face, different person, low quality",
    },
    "coffee_shop_lifestyle": {
        "name": "Coffee Shop Lifestyle",
        "preview_color": "#C05621",
        "positive": (
            "lifestyle portrait in cozy coffee shop, warm window light, "
            "brown cable-knit sweater, coffee cup on wooden table, warm bokeh background, "
            "lifestyle photography, authentic candid feel"
        ),
        "negative": "posed, stiff, outdoor, dark, deformed face, different person, low quality",
    },
    "formal_editorial": {
        "name": "Formal Editorial Look",
        "preview_color": "#2D3748",
        "positive": (
            "high-end editorial portrait, charcoal grey suit, dark gradient background, "
            "dramatic Rembrandt side lighting, GQ magazine style, fashion photography, "
            "ultra sharp, professional"
        ),
        "negative": "casual, outdoor, flat lighting, deformed face, different person, low quality, amateur",
    },
    "beach_sunset_portrait": {
        "name": "Beach Sunset Portrait",
        "preview_color": "#ED8936",
        "positive": (
            "portrait photo at beach during golden sunset, orange and pink sky, "
            "light blue linen shirt, ocean waves in background, warm golden hour light, "
            "lifestyle photography, vacation mood"
        ),
        "negative": "cold light, indoor, studio, deformed face, different person, low quality",
    },
    "urban_street_style": {
        "name": "Urban Daylight Street Style",
        "preview_color": "#718096",
        "positive": (
            "street style portrait on modern city street in daylight, black leather jacket, "
            "sunglasses, urban background, street photography aesthetic, "
            "sharp details, authentic urban mood"
        ),
        "negative": "indoor, nature, deformed face, different person, low quality, posed",
    },
    "artistic_lowkey": {
        "name": "Artistic Low-Key Portrait",
        "preview_color": "#1A202C",
        "positive": (
            "dramatic low-key artistic portrait, single side light source, pure black background, "
            "high contrast, chiaroscuro lighting, fine art photography, deep shadows, intense eyes"
        ),
        "negative": "bright, outdoor, flat lighting, deformed face, different person, low quality, colorful",
    },
    "home_interior_natural": {
        "name": "Home Interior Natural Light",
        "preview_color": "#F6E05E",
        "positive": (
            "natural light portrait at home, near large window, white t-shirt, "
            "indoor plants in background, soft diffused daylight, warm interior tones, "
            "cozy lifestyle photography, authentic home feel"
        ),
        "negative": "outdoor, harsh light, studio, deformed face, different person, low quality",
    },
}

UNIVERSAL_NEGATIVE = (
    "worst quality, low quality, normal quality, lowres, low details, oversaturated, "
    "undersaturated, overexposed, underexposed, grayscale, bw, bad photo, bad photography, "
    "bad art, watermark, signature, text font, username, error, logo, words, letters, "
    "digits, autograph, trademark, name, blur, blurry, grainy, deformed iris, deformed pupils, "
    "bad eyes, semi-realistic, cgi, 3d, render, sketch, cartoon, drawing, anime, duplicate, "
    "morbid, mutilated, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, "
    "mutation, deformed, ugly, bad anatomy, bad proportions, extra limbs, cloned face, "
    "disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, "
    "extra legs, fused fingers, long neck"
)

# Map Android ViewModel style IDs → Python backend style IDs
# (ViewModel uses short IDs; Python backend uses full descriptive IDs)
ANDROID_STYLE_MAP = {
    "golden_hour_rooftop":   "golden_hour_rooftop",
    "rainy_street_cinematic":"rainy_street_cinematic",
    "minimal_studio":        "minimal_studio_portrait",
    "outdoor_forest":        "outdoor_forest_light",
    "coffee_shop":           "coffee_shop_lifestyle",
    "formal_editorial":      "formal_editorial",
    "beach_sunset":          "beach_sunset_portrait",
    "urban_daylight":        "urban_street_style",
    "low_key_portrait":      "artistic_lowkey",
    "home_interior":         "home_interior_natural",
}
