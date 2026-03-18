package com.mommys.app.data.search

import java.util.Locale

/**
 * Tipo de sugerencia
 */
enum class SuggestionType {
    TAG,        // Tag normal de búsqueda
    OPERATOR,   // Operador de búsqueda (order:, rating:, etc.)
    HISTORY,    // Del historial de búsquedas
    SAVED       // Búsqueda guardada
}

/**
 * Modelo de sugerencia
 */
data class Suggestion(
    val text: String,
    val type: SuggestionType,
    val score: Int = 0  // Para ordenar por relevancia
)

/**
 * Gestor de sugerencias de búsqueda.
 * Réplica exacta de RecentSearchesProvider.a de la app original:
 * tags y operadores hardcodeados como lista estática (0ms de carga).
 */
class SuggestionsManager {
    
    // Lista estática exacta de la app original (RecentSearchesProvider.a)
    // 418 tags + operadores hardcodeados en bytecode
    private val predefinedSuggestions = listOf(
        "mammal", "anthro", "female", "hi_res", "male", "solo",
        "clothing", "hair", "fur", "duo", "simple_background",
        "canid", "canine", "video_games", "clothed", "smile",
        "blush", "open_mouth", "looking_at_viewer", "digital_media_(artwork)",
        "tongue", "text", "feral", "horn", "felid", "nintendo",
        "canis", "equid", "biped", "blue_eyes", "white_body",
        "absurd_res", "tongue_out", "english_text", "wings",
        "my_little_pony", "pok\u00e9mon", "pok\u00e9mon_(species)", "standing",
        "teeth", "scalie", "white_background", "group", "claws",
        "white_fur", "friendship_is_magic", "1:1", "eyes_closed",
        "lying", "humanoid", "monochrome", "muscular", "fox",
        "equine", "human", "green_eyes", "dragon", "fingers",
        "long_hair", "toes", "outside", "comic", "piercing",
        "furniture", "feline", "blue_body", "domestic_dog", "2017",
        "brown_body", "2019", "ambiguous_gender", "sitting", "2018",
        "looking_back", "dialogue", "feathers", "2016", "black_body",
        "tuft", "horse", "<3", "wolf", "brown_fur", "eyewear",
        "legwear", "size_difference", "leporid", "young", "solo_focus",
        "narrowed_eyes", "red_eyes", "black_nose", "feathered_wings",
        "headgear", "domestic_cat", "paws", "reptile", "collar",
        "grey_body", "shirt", "on_back", "multicolored_body", "sweat",
        "blonde_hair", "rabbit", "half-closed_eyes", "5_fingers",
        "brown_hair", "black_fur", "fangs", "pantherine", "2015",
        "headwear", "abs", "black_hair", "yellow_body", "pony",
        "muscular_male", "glasses", "multicolored_hair", "anthrofied",
        "yellow_eyes", "ear_piercing", "jewelry", "not_furry", "bed",
        "pecs", "cutie_mark", "blue_fur", "eyelashes", "vein",
        "detailed_background", "unicorn", "avian", "hat", "markings",
        "purple_eyes", "pawpads", "barefoot", "multicolored_fur",
        "grey_fur", "pose", "inside", "bovid", "biceps", "2014",
        "signature", "handwear", "blue_hair", "licking", "footwear",
        "gloves", "hybrid", "disney", "animal_humanoid", "belly",
        "fan_character", "membrane_(anatomy)", "plant", "food",
        "orange_body", "4_toes", "sketch", "rodent", "white_hair",
        "toe_claws", "purple_hair", "stripes", "membranous_wings",
        "hooves", "raised_tail", "yellow_fur", "marine", "kneeling",
        "animated", "sky", "one_eye_closed", "red_hair", "all_fours",
        "weapon", "earth_pony", "rear_view", "water", "bird",
        "short_hair", "portrait", "purple_body", "tree", "pink_hair",
        "girly", "eyebrows", "holding_object", "2013", "chest_tuft",
        "two_tone_body", "stockings", "scales", "unknown_artist",
        "orange_fur", "3d_(artwork)", "16:9", "feet", "speech_bubble",
        "3_toes", "tan_body", "4:3", "tears", "front_view",
        "japanese_text", "red_body", "two_tone_hair", "swimwear",
        "pants", "sonic_the_hedgehog_(series)", "faceless_male",
        "mammal_humanoid", "greyscale", "female/female", "brown_eyes",
        "fluffy", "digitigrade", "pink_body", "necklace", "two_tone_fur",
        "accessory", "black_and_white", "hindpaw", "shoes", "seductive",
        "mostly_nude", "machine", "green_body", "makeup", "drooling",
        "tiger", "beak", "on_top", "caprine", "multicolored_tail",
        "partially_clothed", "grin", "sharp_teeth", "looking_at_another",
        "zootopia", "tan_fur", "armor", "glowing", "2012",
        "winged_unicorn", "tentacles", "purple_fur", "pillow",
        "smaller_male", "4_fingers", "traditional_media_(artwork)",
        "mythology", "grass", "arthropod", "cloud",
        "digital_drawing_(artwork)", "fish", "alien", "mustelid",
        "melee_weapon", "low_res", "happy", "spots", "big_muscles",
        "lion", "demon", "inner_ear_fluff", "alpha_channel", "kemono",
        "shorts", "fluffy_tail", "pok\u00e9morph", "bovine", "bikini",
        "twilight_sparkle_(mlp)", "facial_hair", "pink_nose", "whiskers",
        "humanoid_hands", "armwear", "grey_background", "pink_fur",
        "dress", "cervid", "kissing", "digimon", "ribbons", "skirt",
        "robot", "transformation", "murine", "digimon_(species)",
        "on_bed", "hair_accessory", "holidays", "wet", "messy",
        "bedroom_eyes", "legendary_pok\u00e9mon", "long_ears", "lips",
        "side_view", "red_fur", "beverage", "censored", "spikes",
        "night", "translated", "tattoo", "fire", "undertale", "flower",
        "fully_clothed", "eeveelution", "green_hair", "blue_feathers",
        "socks", "uniform", "boots", "transparent_background",
        "humanoid_pointy_ears", "lizard", "soles", "spitz",
        "facial_piercing", "dipstick_tail", "open_smile", "day",
        "seaside", "rainbow_dash_(mlp)", "scar", "abstract_background",
        "athletic", "judy_hopps", "black_eyes", "fluttershy_(mlp)",
        "looking_down", "first_person_view", "beach", "leash",
        "crossover", "torn_clothing", "shark", "ponytail",
        "full-length_portrait", "2011", "wink", "freckles", "nick_wilde",
        "blue_skin", "belt", "plantigrade", "on_front", "forest",
        "humor",
        // Operadores (exactos de la app original)
        "score:100", "score:>=100", "score:>100", "score:<=100", "score:<100",
        "favcount:100", "favcount:>=100", "favcount:>100", "favcount:<=100", "favcount:<100",
        "commentcount:>20",
        "rating:safe", "rating:questionable", "rating:explicit",
        "type:jpg", "type:png", "type:gif", "type:webm", "type:mp4", "type:swf",
        "ischild:true", "isparent:true",
        "parent:1234", "parent:none",
        "width:1000", "height:1000", "mpixels:1",
        "ratio:1.33", "filesize:200KB",
        "duration:>120",
        "date:2020-10-27", "date:today", "date:yesterday",
        "status:active", "status:pending", "status:flagged",
        "inpool:true",
        "order:id", "order:random",
        "order:score", "order:score_asc",
        "order:favcount", "order:favcount_asc",
        "order:tagcount", "order:tagcount_asc",
        "order:desclength", "order:desclength_asc",
        "order:comments", "order:comments_asc",
        "order:mpixels", "order:mpixels_asc",
        "order:filesize", "order:filesize_asc",
        "order:landscape", "order:portrait",
        "order:change",
        "order:duration", "order:duration_asc"
    )
    
    /**
     * Obtiene sugerencias que coincidan con el query
     * @param query El texto a buscar (generalmente el último tag que se está escribiendo)
     * @param limit Máximo de sugerencias a retornar
     */
    fun getSuggestions(query: String, limit: Int = 50): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        
        val queryLower = query.lowercase(Locale.getDefault())
        
        return predefinedSuggestions.asSequence()
            .filter { tag ->
                val tagLower = tag.lowercase()
                tagLower.startsWith(queryLower) || tagLower.contains(queryLower)
            }
            .take(limit)
            .map { tag ->
                val tagLower = tag.lowercase()
                val type = if (tag.contains(":")) SuggestionType.OPERATOR else SuggestionType.TAG
                val score = when {
                    tagLower == queryLower -> 200
                    tagLower.startsWith(queryLower) -> 100
                    else -> 25
                }
                Suggestion(tag, type, score)
            }
            .sortedByDescending { it.score }
            .toList()
    }
    
    /**
     * Verifica si un texto es un operador de búsqueda
     */
    fun isOperator(text: String): Boolean {
        val prefixes = listOf(
            "score:", "favcount:", "commentcount:", "rating:", "type:",
            "ischild:", "isparent:", "parent:", "width:", "height:",
            "mpixels:", "ratio:", "date:", "duration:", "status:",
            "inpool:", "order:", "fav:", "pool:", "set:", "user:",
            "uploaderid:", "approverid:", "commenter:", "noter:",
            "noteupdater:", "artcomm:", "delreason:", "source:",
            "description:", "note:", "id:", "tagcount:", "gentags:",
            "arttags:", "chartags:", "copytags:", "spectags:", "metatags:",
            "invtags:", "lortags:", "-"
        )
        val lower = text.lowercase()
        return prefixes.any { lower.startsWith(it) }
    }
    
    /**
     * Obtiene sugerencias populares/recomendadas
     */
    fun getPopularSuggestions(limit: Int = 20): List<Suggestion> {
        val popular = listOf(
            "solo", "female", "male", "duo", "anthro", "feral",
            "order:score", "order:favcount", "rating:safe",
            "hi_res", "absurd_res", "animated"
        )
        
        return popular.take(limit).mapIndexed { index, text ->
            val type = if (isOperator(text)) SuggestionType.OPERATOR else SuggestionType.TAG
            Suggestion(text, type, 100 - index)
        }
    }
    
    /**
     * Obtiene la cantidad total de sugerencias disponibles
     */
    fun getTotalSuggestionsCount(): Int {
        return predefinedSuggestions.size
    }
}
