"""Training + evaluation data for the specialty classifier.

IMPORTANT: this is a small, hand-curated *demo* dataset, not clinical data.
Training sentences are generated from symptom phrases + sentence templates;
the evaluation set is hand-written separately so the score is not just the
model re-reading its own templates.
"""
import random

SYMPTOMS: dict[str, list[str]] = {
    "Cardiology": [
        "chest pain", "chest tightness", "pressure in my chest", "heart palpitations", "racing heart",
        "irregular heartbeat", "breathlessness while climbing stairs", "swollen ankles", "high blood pressure",
        "pain spreading to my left arm", "fainting episodes", "heart skipping beats", "chest discomfort on exertion",
        "feeling my heart pounding",
    ],
    "Dermatology": [
        "itchy skin", "skin rash", "red patches on my skin", "acne", "pimples on my face", "dry flaky skin",
        "hives", "eczema", "hair loss in patches", "dandruff", "a mole that changed color", "peeling skin",
        "blisters on my hands", "fungal infection between my toes", "dark spots on my face", "nail discoloration",
    ],
    "Endocrinology": [
        "increased thirst", "frequent urination", "unexplained weight gain", "weight loss despite eating well",
        "swelling in my neck", "feeling cold all the time", "high blood sugar", "diabetes", "excessive sweating and heat intolerance",
        "irregular periods", "tingling feet with high sugar levels", "thyroid problems", "goiter", "sugar levels fluctuating",
    ],
    "Neurology": [
        "severe headache", "migraine", "numbness in my hand", "tingling in my limbs", "seizures", "memory loss",
        "tremors", "dizziness and a spinning sensation", "difficulty speaking", "weakness on one side of the body",
        "blurred vision with headache", "loss of balance", "confusion", "shaking hands", "pins and needles in my legs",
    ],
    "Orthopedics": [
        "knee pain", "lower back pain", "joint pain", "shoulder pain", "swollen joint", "a possible fracture",
        "sprained ankle", "hip pain when walking", "neck stiffness", "wrist pain", "difficulty bending my knee",
        "muscle strain", "heel pain", "bone pain after a fall", "stiff joints in the morning",
    ],
    "ENT": [
        "sore throat", "ear pain", "blocked nose", "runny nose", "hearing loss", "ringing in my ears",
        "sinus pressure", "nosebleeds", "hoarse voice", "difficulty swallowing", "swollen tonsils",
        "loss of smell", "loud snoring", "discharge from my ear", "pain while swallowing",
    ],
    "Pulmonology": [
        "persistent cough", "wheezing", "shortness of breath", "coughing up phlegm", "chest congestion",
        "asthma attacks", "breathlessness at night", "a cough that has lasted for weeks", "tightness while breathing",
        "a whistling sound when I breathe", "difficulty breathing in cold air", "recurring bronchitis",
    ],
    "Gastroenterology": [
        "stomach pain", "acidity", "heartburn", "nausea", "vomiting", "bloating", "constipation", "diarrhea",
        "loss of appetite", "abdominal cramps", "indigestion", "excess gas", "a burning sensation in my stomach",
        "pain after eating", "yellowing of my eyes",
    ],
    "Psychiatry": [
        "anxiety", "panic attacks", "feeling very sad", "depression", "insomnia", "trouble sleeping at night",
        "loss of interest in everything", "mood swings", "constant worry", "feeling overwhelmed by stress",
        "difficulty concentrating", "overthinking everything", "irritability", "recurring nightmares", "feeling hopeless",
    ],
    "General Medicine": [
        "fever", "body aches", "fatigue", "general weakness", "chills", "flu-like symptoms", "feeling tired all the time",
        "a mild cold", "a viral infection", "low energy", "a mild headache", "night sweats", "feeling generally unwell",
        "muscle aches", "a low-grade fever for several days", "a routine health check-up",
    ],
}


# More natural wordings / synonyms per specialty (general vocabulary, merged into SYMPTOMS below).
EXTRA: dict[str, list[str]] = {
    "Cardiology": ["an irregular pulse", "feeling faint", "nearly passing out", "swelling in my legs", "getting out of breath easily",
                   "a squeezing feeling in the chest", "my heart racing at night", "chest heaviness", "high blood pressure readings",
                   "cholesterol problems", "my heart fluttering", "a pounding heart when I exercise"],
    "Dermatology": ["a red itchy rash", "oily skin", "a flaking scalp", "hair fall", "a skin allergy", "ringworm", "warts", "boils",
                    "skin turning dark", "small bumps on my skin", "scaly patches", "itching all over my body", "white patches on my skin",
                    "acne scars", "itchy bumps after using a new soap"],
    "Endocrinology": ["drinking a lot of water", "peeing a lot", "sudden weight gain", "always feeling cold", "diabetes management",
                      "abnormal thyroid levels", "blood sugar readings that are too high", "PCOS", "a hormonal imbalance",
                      "excessive hunger", "wounds that heal slowly", "an enlarged thyroid", "losing weight with a huge appetite"],
    "Neurology": ["throbbing headaches", "sensitivity to light", "forgetting recent conversations", "forgetfulness", "numbness and tingling in my hand",
                  "dropping things", "electric shock sensations", "fits", "trouble with my memory", "facial numbness", "persistent headaches",
                  "weakness in my arm", "being unsteady when I walk", "muscle twitching", "hands that tremble"],
    "Orthopedics": ["knee pain when climbing stairs", "a swollen painful ankle", "a sports injury", "pain shooting down my leg", "sciatica",
                    "a slipped disc", "arthritis", "elbow pain", "a stiff neck and shoulder", "cracking joints", "a torn ligament",
                    "a broken bone", "a badly swollen wrist after a fall", "sore knuckles and stiff fingers"],
    "ENT": ["pain on swallowing", "enlarged tonsils", "a stuffy nose", "pressure around my eyes", "not being able to hear well", "an ear infection",
            "a blocked ear", "post nasal drip", "constant sneezing", "voice changes", "allergic rhinitis", "a scratchy throat",
            "losing my voice", "pus coming out of the ear"],
    "Pulmonology": ["a dry cough for a month", "feeling wheezy in the evening", "trouble breathing when lying down", "a whistling in my chest",
                    "thick mucus every morning", "breathing trouble", "a smoker's cough", "being short of breath at rest", "a chronic cough",
                    "lung problems", "an inhaler that is not helping", "a nagging cough that is worse at night"],
    "Gastroenterology": ["a bloated belly", "loose motions", "burning pain in the upper stomach", "belly pain", "not passing stool", "acid reflux",
                         "vomiting after meals", "gas pain", "stomach cramps", "feeling full quickly", "jaundice", "gastric problems",
                         "a tummy ache", "pain in the right side of my belly after fatty food", "acid coming up into my throat"],
    "Psychiatry": ["sudden waves of fear", "not being able to calm down", "being unable to sleep for weeks", "feeling very low", "worrying about everything",
                   "not being able to switch my thoughts off", "feeling empty", "losing interest in things I enjoyed", "a lack of motivation",
                   "social anxiety", "obsessive thoughts", "feeling nervous all the time", "low mood", "a racing mind full of worries"],
    "General Medicine": ["a fever and a sore body", "feeling exhausted all the time", "being tired even after resting", "feeling run down",
                         "just feeling unwell", "wanting a general check up", "seasonal flu", "a fever with chills", "a loss of energy",
                         "persistent tiredness", "feeling feverish", "a temperature and aches all over", "catching a cold"],
}
for _spec, _terms in EXTRA.items():
    SYMPTOMS[_spec].extend(_terms)

TEMPLATES_1 = [
    "I have {a}", "I've been having {a}", "{a}", "suffering from {a}", "my main problem is {a}",
    "I am experiencing {a}", "{a} since yesterday", "dealing with {a} for a week", "I think I have {a}",
    "doctor, I need help with {a}",
    "lately I keep getting {a}", "{a} that gets worse in the evening", "I'm really bothered by {a}",
    "for a few weeks I've noticed {a}", "I came in because of {a}",
]
TEMPLATES_2 = [
    "I have {a} and {b}", "{a} and {b}", "suffering from {a} and {b} for the past few days",
    "experiencing {a} along with {b}", "I've noticed {a}, and also {b}", "{a}, {b}", "doctor, {a}. also {b}",
    "for two weeks now I have had {a} and {b}", "I am worried about {a} and {b}",
    "lately I've been noticing {a}. It's bothering me and now there is {b} as well",
    "it started with {a} and then {b} appeared", "whenever it happens I get {a} and {b}",
]
TEMPLATES_3 = [
    "{a}, {b} and {c}", "I have {a}, {b} and also {c}", "suffering from {a}, {b}, and {c}",
    "lately I get {a}, {b} and {c}",
]


def generate_training_data(per_class: int = 400, seed: int = 7) -> tuple[list[str], list[str]]:
    rng = random.Random(seed)
    texts: list[str] = []
    labels: list[str] = []
    specialties = list(SYMPTOMS)
    for spec in specialties:
        terms = SYMPTOMS[spec]
        others = [t for s in specialties if s != spec for t in SYMPTOMS[s]]
        for _ in range(per_class):
            n = rng.choices([1, 2, 3], weights=[3, 5, 2])[0]
            picked = rng.sample(terms, min(n, len(terms)))
            # ~15% of samples mention one symptom from another specialty,
            # so the model learns that mixed descriptions still have a "main" topic.
            if n >= 2 and rng.random() < 0.15:
                picked[-1] = rng.choice(others)
            template = rng.choice({1: TEMPLATES_1, 2: TEMPLATES_2, 3: TEMPLATES_3}[len(picked)])
            texts.append(template.format(**dict(zip("abc", picked))))
            labels.append(spec)
    return texts, labels


# DEV set: hand-written sentences used while iterating on the vocabulary (so it is partly tuned-on).
DEV_SET: list[tuple[str, str]] = [
    ("My heart has been racing at night and I get a squeezing feeling in my chest when I walk fast", "Cardiology"),
    ("I feel my pulse is all over the place and I got dizzy and nearly passed out", "Cardiology"),
    ("My feet are swollen by evening and I get out of breath on stairs", "Cardiology"),
    ("There is an itchy red rash spreading on my arms", "Dermatology"),
    ("I keep getting pimples and my skin is very oily", "Dermatology"),
    ("My scalp is flaking and my hair is falling out a lot", "Dermatology"),
    ("I am drinking water constantly and running to the bathroom all day", "Endocrinology"),
    ("I gained a lot of weight suddenly and always feel cold, my periods are irregular too", "Endocrinology"),
    ("My blood sugar readings have been very high this month", "Endocrinology"),
    ("I get terrible throbbing headaches with light sensitivity", "Neurology"),
    ("My left hand goes numb and tingles and I sometimes drop things", "Neurology"),
    ("I keep forgetting recent conversations and get confused easily", "Neurology"),
    ("My knee hurts badly when I climb stairs and it feels swollen", "Orthopedics"),
    ("I twisted my ankle playing football and it is swollen and painful", "Orthopedics"),
    ("Pain in my lower back shoots down my leg when I sit for long", "Orthopedics"),
    ("My throat hurts a lot when I swallow and my tonsils look big", "ENT"),
    ("I have a blocked nose and pressure around my eyes for days", "ENT"),
    ("There is a constant ringing in my ear and I can't hear well", "ENT"),
    ("I have had a dry cough for over a month and get wheezy in the evening", "Pulmonology"),
    ("It is hard to breathe when I lie down and I hear whistling in my chest", "Pulmonology"),
    ("I cough up thick yellow mucus every morning", "Pulmonology"),
    ("I have a burning pain in my upper stomach after meals", "Gastroenterology"),
    ("I've had loose motions and cramps since yesterday", "Gastroenterology"),
    ("My belly is bloated and I have been constipated for a week", "Gastroenterology"),
    ("I get sudden waves of fear and my hands sweat, I can't calm down", "Psychiatry"),
    ("I haven't been able to sleep for weeks and I feel very low", "Psychiatry"),
    ("I worry about everything and cannot switch my thoughts off", "Psychiatry"),
    ("I have had a fever and sore body for three days", "General Medicine"),
    ("I feel exhausted all the time even after resting", "General Medicine"),
    ("I want a general check up, nothing specific, just feeling unwell", "General Medicine"),
]


# TEST set: written once, after the vocabulary work, and never used for tuning.
TEST_SET: list[tuple[str, str]] = [
    ("Whenever I run my heart feels like it is hammering and there is a heavy weight on my chest", "Cardiology"),
    ("My blood pressure was 170 over 100 at the pharmacy yesterday", "Cardiology"),
    ("Small itchy bumps appeared on my neck after I tried a new soap", "Dermatology"),
    ("My acne keeps coming back and leaves marks", "Dermatology"),
    ("I keep losing weight even though my appetite is huge and my hands shake", "Endocrinology"),
    ("My thyroid test came back abnormal and I want to talk about it", "Endocrinology"),
    ("I wake up with a pounding headache and see flashing lights", "Neurology"),
    ("Sometimes one side of my face feels numb for a few minutes", "Neurology"),
    ("My hands tremble when I hold a cup of tea", "Neurology"),
    ("I fell off my bike and my wrist is badly swollen", "Orthopedics"),
    ("My shoulder aches when I lift my arm above my head", "Orthopedics"),
    ("Morning stiffness in my fingers and sore knuckles", "Orthopedics"),
    ("My son's ear is hurting and some pus is coming out", "ENT"),
    ("I lose my voice every time I catch a cold and my throat feels scratchy", "ENT"),
    ("I sneeze all day and my nose is always dripping", "ENT"),
    ("I get breathless even at rest and my inhaler is not helping", "Pulmonology"),
    ("A nagging cough that gets worse at night", "Pulmonology"),
    ("I throw up after almost every meal and have stomach cramps", "Gastroenterology"),
    ("Sharp pain on the right side of my belly after fatty food", "Gastroenterology"),
    ("I feel like acid is coming up into my throat after dinner", "Gastroenterology"),
    ("I feel hopeless and I don't enjoy anything anymore", "Psychiatry"),
    ("I get anxious in crowds and my mind keeps racing with worries", "Psychiatry"),
    ("I have a temperature and feel achy all over", "General Medicine"),
    ("I caught a cold and feel run down", "General Medicine"),
]
