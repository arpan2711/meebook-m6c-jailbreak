"""Map free-form subject tags (from book metadata / online fetch) onto a short list of shelves.

Decide fiction vs non-fiction first, then the first matching rule on that side wins (specific
shelves before broad ones). Books with no usable tags go to "Other" for manual review.
"""
import re

FICTION = [
    ("Comics & Manga", r"comic|manga|graphic novel"),
    ("Children & Young Adult", r"juvenile|children|young adult|\bya\b|grades? \d|teen"),
    ("Horror", r"horror|vampire|zombie|ghost stor"),
    ("Science Fiction & Fantasy", r"science fiction|sci-fi|fantasy|dystopia|space opera|alternat\w* histor|magic|paranormal"),
    ("Mystery & Thriller", r"mystery|detective|thriller|suspense|crime|sleuth|police|espionage|spy"),
    ("Romance", r"romance|love stor"),
    ("Literary Fiction & Classics", r"."),  # any other fiction
]

NONFICTION = [
    ("Comics & Manga", r"comic|manga|graphic novel"),
    ("Children & Young Adult", r"juvenile|children's books|young adult"),
    ("Cooking & Food", r"cook|recipe|vegan|baking|\bdiet|nutrition|food"),
    ("Chess & Games", r"chess|\bgames?\b|puzzle"),
    ("Biography & Memoir", r"biograph|memoir|autobiograph|diaries"),
    ("Science & Nature", r"science|physics|biology|evolution|quantum|nature|animals|pets|dogs|mathemat|astronom|ecology|medical|health|survival|outdoor"),
    ("Philosophy & Religion", r"philosoph|religio|islam|christian|buddh|spiritual|theolog|ethics"),
    ("Psychology & Self-Help", r"psycholog|self.help|happiness|motivat|personal growth|success|relationships|parenting|productiv"),
    ("Business & Economics", r"business|econom|finance|money|management|capitalism|entrepreneur"),
    ("Politics & Society", r"politic|social|sociology|law|current affairs|journalism|true crime|terroris"),
    ("History", r"histor|world war|ancient|medieval|civilization|empire"),
    ("Literary Fiction & Classics", r"literary criticism|poetry|drama|essays"),
    ("Reference & Language", r"reference|dictionar|language|grammar|travel"),
]

NOISE = re.compile(r"^(general|fiction - general|united states|american|english|large type books|"
                   r"electronic books|ebook|accessible book|protected daisy|in library|overdrive|"
                   r"open library staff picks|nyt:.*)$", re.I)


def is_fiction(hay):
    if re.search(r"non-?fiction", hay):
        return False
    return bool(re.search(r"fiction|novel|stories|fantasy|romance|thriller|mystery|detective|sagas|fairy tales", hay))


def genre_for(tags, title=""):
    useful = [t for t in tags if t and not NOISE.match(t.strip())]
    hay = " | ".join(useful).lower()
    if not hay:
        return "Other"
    rules = FICTION if is_fiction(hay) else NONFICTION
    for name, pattern in rules:
        if re.search(pattern, hay):
            return name
    return "Other"
