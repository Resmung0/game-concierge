package com.example.context.data

import com.example.context.model.GameKnowledgeItem
import com.example.context.model.GuideCategory

interface GameKnowledgeRepository {
    fun getGuidesForGame(gameId: String): List<GameKnowledgeItem>
    fun queryGuides(gameId: String, query: String, category: GuideCategory? = null): List<GameKnowledgeItem>
    fun getSupportedGames(): List<GameSummary>
}

data class GameSummary(
    val id: String,
    val title: String,
    val genre: String,
    val guideCount: Int
)

class InMemoryGameKnowledgeRepository : GameKnowledgeRepository {

    private val guides = listOf(
        // Elden Ring Guides
        GameKnowledgeItem(
            id = "er_margit",
            gameId = "elden_ring",
            category = GuideCategory.BOSS,
            title = "Margit, the Fell Omen",
            locationOrBoss = "Castleward Tunnel, Stormhill",
            summary = "Early game gatekeeper boss with delayed overhead attacks and rapid holy dagger throws.",
            tacticalSteps = listOf(
                "Maintain mid-range spacing to bait the lunging overhead cane slam.",
                "Roll diagonally forward through the sweep attack; rolling backward catches the delay.",
                "Use Margit's Shackle (purchased from Patches) up to 2 times in Phase 1 for free heavy punish windows.",
                "Phase 2 hammer slams deal heavy holy damage. Roll toward his rear and punish during recovery."
            ),
            tags = listOf("boss", "margit", "stormhill", "shackle", "parry", "holy")
        ),
        GameKnowledgeItem(
            id = "er_malenia",
            gameId = "elden_ring",
            category = GuideCategory.BOSS,
            title = "Malenia, Blade of Miquella",
            locationOrBoss = "Elphael, Brace of the Haligtree",
            summary = "Endgame superboss who heals on every hit and unleashes the deadly Waterfowl Dance.",
            tacticalSteps = listOf(
                "When she levitates for Waterfowl Dance, immediately sprint backward for bursts 1 and 2, then roll through burst 3.",
                "Throw a Freezing Pot the moment she ascends to stagger her out of the startup animation.",
                "Phase 2 Scarlet Aeonia: Run underneath her landing point and sprint past it. Wait for petal explosion to subside before casting long-range spells."
            ),
            tags = listOf("boss", "malenia", "haligtree", "waterfowl", "scarlet rot", "freeze")
        ),
        GameKnowledgeItem(
            id = "er_heretical_rise",
            gameId = "elden_ring",
            category = GuideCategory.PUZZLE,
            title = "Heretical Rise Invisible Bridge",
            locationOrBoss = "Mountaintops of the Giants",
            summary = "Riddle requiring crossing an invisible chasm to remove the seal on the tower.",
            tacticalSteps = listOf(
                "Cross the ruined ravine from the frozen lake balcony opposite the sealed tower.",
                "Drop Rainbow Stones or fire arrows with glowing fletching onto the empty air to trace the solid invisible floor.",
                "Notice the faint frosty mist drifting on the bridge: if the mist swirls, the surface is walkable.",
                "Near the end, take the ascending left spiral staircase to enter the balcony."
            ),
            tags = listOf("puzzle", "rise", "tower", "invisible", "seal", "rainbow stones")
        ),
        GameKnowledgeItem(
            id = "er_golden_seeds",
            gameId = "elden_ring",
            category = GuideCategory.COLLECTIBLE,
            title = "Early Flask Upgrades (Golden Seeds)",
            locationOrBoss = "Limgrave & Weeping Peninsula",
            summary = "Golden seed sapling locations to expand your healing and mana flask capacity early.",
            tacticalSteps = listOf(
                "Seed 1: Just outside Stormveil Castle gate along the main ascent road.",
                "Seed 2: Inside Fringefolk Hero's Grave (defeating the Ulcerated Tree Spirit).",
                "Seed 3: Under the phantom tree in Weeping Peninsula near Castle Morne rampart."
            ),
            tags = listOf("collectible", "flasks", "seeds", "healing", "early game")
        ),

        // Hollow Knight Guides
        GameKnowledgeItem(
            id = "hk_mantis_lords",
            gameId = "hollow_knight",
            category = GuideCategory.BOSS,
            title = "Mantis Lords",
            locationOrBoss = "Mantis Village, Fungal Wastes",
            summary = "Rhythmic combat encounter against three mantis chieftains.",
            tacticalSteps = listOf(
                "Phase 1 (Single Lord): Jump over the dash attack and down-slash (pogo) on her head.",
                "When she drops from above, dash sideways and immediately turn for a quick nail slash.",
                "Phase 2 (Dual Lords): Stand in the exact center of the arena during the high boomerang throw to heal 1 mask safely.",
                "Equip Shaman Stone and Mark of Pride for superior reach and Vengeful Spirit damage."
            ),
            tags = listOf("boss", "mantis", "fungal wastes", "pogo", "charm")
        ),
        GameKnowledgeItem(
            id = "hk_radiance",
            gameId = "hollow_knight",
            category = GuideCategory.BOSS,
            title = "The Radiance",
            locationOrBoss = "Temple of the Black Egg",
            summary = "True final boss of Hallownest, fought in the dream realm atop the Hollow Knight.",
            tacticalSteps = listOf(
                "Use Abyss Shriek beneath her sprite for immense multiple-hit burst damage.",
                "Nail bounce off horizontal sword walls with Shade Cloak dash when beams intersect.",
                "Phase 4 (Ascent): Keep climbing upward steadily; avoid lingering on lower platforms as void rises."
            ),
            tags = listOf("boss", "radiance", "abyss shriek", "dream", "shade cloak")
        ),
        GameKnowledgeItem(
            id = "hk_pale_ore",
            gameId = "hollow_knight",
            category = GuideCategory.COLLECTIBLE,
            title = "Pale Ore Locations for Pure Nail",
            locationOrBoss = "Across Hallownest",
            summary = "Required materials to upgrade the Knight's nail at the Nailsmith in City of Tears.",
            tacticalSteps = listOf(
                "Ore 1: Ancient Basin, guarded by Lesser Mawleks past the tram.",
                "Ore 2: Crystal Peak, crown summit above the Hallownest statue.",
                "Ore 3: Deepnest, behind Nosk in the deepest lair."
            ),
            tags = listOf("collectible", "pale ore", "nailsmith", "pure nail", "upgrade")
        ),

        // Zelda: Tears of the Kingdom Guides
        GameKnowledgeItem(
            id = "totk_colgera",
            gameId = "zelda_totk",
            category = GuideCategory.BOSS,
            title = "Colgera (Scourge of the Wind Temple)",
            locationOrBoss = "Wind Temple, Sky of Hebra",
            summary = "Gigantic airborne ice centipede engaged entirely in mid-air free fall.",
            tacticalSteps = listOf(
                "Do not waste weapon durability on melee; this is an aerial archery and diving battle.",
                "When Colgera exposes its glowing underside ice segments, dive directly through them or shoot arrows.",
                "Fuse Keese Wings or Aerocuda Eyeballs to arrows to hit the segments from long range through updrafts."
            ),
            tags = listOf("boss", "colgera", "wind temple", "hebra", "aerial", "fuse")
        ),
        GameKnowledgeItem(
            id = "totk_jonsau_shrine",
            gameId = "zelda_totk",
            category = GuideCategory.PUZZLE,
            title = "Jonsau Shrine (A Deep Force)",
            locationOrBoss = "Lanayru Wetlands",
            summary = "Buoyancy physics puzzle utilizing Ultrahand to push floating spheres deep into water.",
            tacticalSteps = listOf(
                "Grab the yellow sphere with Ultrahand and submerge it to the bottom of the pool.",
                "Release it directly beneath the hanging target: water buoyancy launches it upward to trigger the switch.",
                "For the final gate, push the floating wooden raft under the target to launch it into the overhead trigger."
            ),
            tags = listOf("puzzle", "shrine", "jonsau", "ultrahand", "recall", "buoyancy")
        ),

        // Cyberpunk 2077 Guides
        GameKnowledgeItem(
            id = "cp_adam_smasher",
            gameId = "cyberpunk_2077",
            category = GuideCategory.BOSS,
            title = "Adam Smasher",
            locationOrBoss = "Arasaka Tower Atrium",
            summary = "Heavily cyberized Arasaka combat cyborg with rocket launcher shoulders and heavy shotgun.",
            tacticalSteps = listOf(
                "Destroy his right shoulder missile launcher first to disable barrage attacks.",
                "Activate Sandevistan or Kerenzikov to dash behind him when he charges with his shotgun.",
                "Use EMP grenades or Cyberware Malfunction quickhacks to disrupt his sub-dermal defenses."
            ),
            tags = listOf("boss", "adam smasher", "arasaka", "sandevistan", "emp", "cyberware")
        )
    )

    private val supportedGames = listOf(
        GameSummary("elden_ring", "Elden Ring", "Action RPG", 4),
        GameSummary("hollow_knight", "Hollow Knight", "Metroidvania", 3),
        GameSummary("zelda_totk", "Zelda: TotK", "Open World Adventure", 2),
        GameSummary("cyberpunk_2077", "Cyberpunk 2077", "Sci-Fi RPG", 1)
    )

    override fun getGuidesForGame(gameId: String): List<GameKnowledgeItem> {
        val normalized = normalizeGameId(gameId)
        return guides.filter { it.gameId == normalized }
    }

    override fun queryGuides(gameId: String, query: String, category: GuideCategory?): List<GameKnowledgeItem> {
        val normalized = normalizeGameId(gameId)
        val gameGuides = guides.filter { it.gameId == normalized }
        val q = query.trim().lowercase()
        val tokens = q.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 3 }

        return gameGuides.filter { item ->
            val matchesCategory = category == null || item.category == category
            val matchesQuery = q.isEmpty() ||
                    item.title.lowercase().contains(q) ||
                    item.locationOrBoss.lowercase().contains(q) ||
                    item.summary.lowercase().contains(q) ||
                    item.tags.any { tag -> q.contains(tag) || tag.contains(q) } ||
                    tokens.any { token ->
                        item.title.lowercase().contains(token) ||
                        item.locationOrBoss.lowercase().contains(token) ||
                        item.tags.any { it.contains(token) } ||
                        item.tacticalSteps.any { it.lowercase().contains(token) }
                    }
            matchesCategory && matchesQuery
        }
    }

    override fun getSupportedGames(): List<GameSummary> = supportedGames

    private fun normalizeGameId(nameOrId: String): String {
        val lower = nameOrId.lowercase().replace(":", "").replace(" ", "_").trim()
        return when {
            lower.contains("elden") -> "elden_ring"
            lower.contains("hollow") -> "hollow_knight"
            lower.contains("zelda") || lower.contains("totk") -> "zelda_totk"
            lower.contains("cyber") -> "cyberpunk_2077"
            else -> lower
        }
    }
}
