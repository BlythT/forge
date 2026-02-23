package forge.ai;

import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

public class ComputerUtilTests extends AITest {
    // Mulligan scoring relies on deck composition.
    private void setupDeck(Player p) {
        // Standard 60-card deck: 24 lands, 36 spells
        // Minus 7 for starting hand (3 lands, 4 spells) = 21 lands, 32 spells
        for (int i = 0; i < 21; i++) {
            addCardToZone("Forest", p, ZoneType.Library);
        }
        for (int i = 0; i < 32; i++) {
            addCardToZone("Grizzly Bears", p, ZoneType.Library);
        }
    }

    @Test
    public void testReturnsEmptyWhenNothingToReturn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Island", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 0);
        assertEquals(0, result.size());
    }

    @Test
    public void testReturnsFullHandWhenAllMustBeReturned() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Island", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertEquals(2, result.size());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThrowsWhenRequestingMoreCardsThanInHand() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));

        ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
    }

    @Test
    public void testPrefersReturningExcessLands() {
        // A hand of 6 lands and 1 spell: should return lands, not the spell
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 6; i++) {
            hand.add(createCard("Forest", p));
        }
        Card bear = createCard("Runeclaw Bear", p);
        hand.add(bear);

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertFalse("Should not return the only spell", result.contains(bear));
    }

    @Test
    public void testPrefersReturningHighCostSpellsWhenLandLight() {
        // A hand with 2 lands and a mix of cheap/expensive spells: should return expensive ones
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Mountain", p));
        hand.add(createCard("Mountain", p));
        Card expensive = createCard("Stone Golem", p); // 5 CMC
        hand.add(expensive);
        hand.add(createCard("Raging Goblin", p)); // 1 CMC
        hand.add(createCard("Raging Goblin", p)); // 1 CMC
        hand.add(createCard("Nest Robber", p));   // 2 CMC
        hand.add(createCard("Nest Robber", p));   // 2 CMC

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 1);
        assertTrue("Should return the highest CMC card when land-light, but returned: " + result, result.contains(expensive));
    }

    @Test
    public void testFullyCastableCorrectColors() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card bear = createCard("Grizzly Bears", p);

        float score = ComputerUtil.scoreCastability(bear, lands.size(), availableColors, hasColorless);
        assertEquals("Fully castable card with correct colors should score 1.0", 1.0f, score, 0.01f);
    }

    @Test
    public void testFullyCastableWrongColors() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Island", p));
        lands.add(createCard("Island", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card bear = createCard("Grizzly Bears", p);

        float score = ComputerUtil.scoreCastability(bear, lands.size(), availableColors, hasColorless);
        assertEquals("Green card with only blue mana should score 0.0", 0.0f, score, 0.01f);
    }

    @Test
    public void testPartialManaCorrectColors() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card golem = createCard("Verdant Force", p);

        float score = ComputerUtil.scoreCastability(golem, lands.size(), availableColors, hasColorless);
        assertTrue("Semi-castable high CMC card should score below 0.15, got: " + score, score < 0.15f);
    }

    @Test
    public void testAlmostCastableOneShort() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card elvish = createCard("Trained Armodon", p);

        float score = ComputerUtil.scoreCastability(elvish, lands.size(), availableColors, hasColorless);
        assertTrue("One land short should score between 0.3 and 0.6, got: " + score, score > 0.3f && score < 0.6f);
    }

    @Test
    public void testZeroCostCardAlwaysFullScore() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        byte availableColors = 0;
        boolean hasColorless = false;
        Card lotus = createCard("Lotus Petal", p);

        float score = ComputerUtil.scoreCastability(lotus, 0, availableColors, hasColorless);
        assertEquals("Zero cost card should always score 1.0", 1.0f, score, 0.01f);
    }

    @Test
    public void testLandScoresZero() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        byte availableColors = 0;
        boolean hasColorless = false;
        Card forest = createCard("Forest", p);

        float score = ComputerUtil.scoreCastability(forest, 1, availableColors, hasColorless);
        assertEquals("Lands should always score 0.0", 0.0f, score, 0.01f);
    }

    @Test
    public void testPhyrexianManaIgnoredForColorRatio() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card probe = createCard("Gitaxian Probe", p);

        float score = ComputerUtil.scoreCastability(probe, lands.size(), availableColors, hasColorless);
        assertEquals("Phyrexian mana pip should not penalize color ratio", 1.0f, score, 0.01f);
    }

    @Test
    public void testHighCMCScoresLowerThanLowCMC() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);

        Card cheap = createCard("Grizzly Bears", p);
        Card expensive = createCard("Verdant Force", p);

        float cheapScore = ComputerUtil.scoreCastability(cheap, lands.size(), availableColors, hasColorless);
        float expensiveScore = ComputerUtil.scoreCastability(expensive, lands.size(), availableColors, hasColorless);

        assertTrue("Cheaper card should score higher with same lands (cheap=" + cheapScore + ", expensive=" + expensiveScore + ")",
                cheapScore > expensiveScore);
    }

    @Test
    public void testColorlessSpecificPip() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card eldrazi = createCard("Matter Reshaper", p);

        float score = ComputerUtil.scoreCastability(eldrazi, lands.size(), availableColors, hasColorless);
        assertTrue("Card requiring {C} pip should score below 0.5 without colorless source, got: " + score, score < 0.5f);
    }

    @Test
    public void testColorlessPipWithColorlessProducingLand() {
        // {C} pip should score well when we have a colorless source like Wastes
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Wastes", p));
        lands.add(createCard("Wastes", p));
        lands.add(createCard("Wastes", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card eldrazi = createCard("Matter Reshaper", p); // {2}{C}

        float score = ComputerUtil.scoreCastability(eldrazi, lands.size(), availableColors, hasColorless);
        assertEquals("Card requiring {C} with colorless sources should score 1.0", 1.0f, score, 0.01f);
    }

    @Test
    public void testAllLandHandReturnsLands() {
        // 7 lands, 0 spells: should return lands (nothing to do with them all)
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 7; i++) {
            hand.add(createCard("Forest", p));
        }

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 3);
        // Every returned card should be a land: there are no spells to protect
        for (Card c : result) {
            assertTrue("All-land hand: returned cards should be lands", c.isLand());
        }
    }

    @Test
    public void testAllSpellHandReturnsSpells() {
        // 7 spells, 0 lands: should return spells (can't cast anything anyway),
        // specifically the most expensive ones
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        Card expensive1 = createCard("Stone Golem", p);  // 5 CMC
        Card expensive2 = createCard("Verdant Force", p); // 8 CMC
        hand.add(expensive1);
        hand.add(expensive2);
        hand.add(createCard("Raging Goblin", p));
        hand.add(createCard("Raging Goblin", p));
        hand.add(createCard("Raging Goblin", p));
        hand.add(createCard("Nest Robber", p));
        hand.add(createCard("Nest Robber", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertTrue("No-land hand should return the highest CMC spells", result.contains(expensive2));
        assertTrue("No-land hand should return the second-highest CMC spell", result.contains(expensive1));
    }

    @Test
    public void testDuplicateHighCostSpellsReturnedBeforeUnique() {
        // If there are two copies of the same expensive card, both are lower-priority
        // than a unique cheap card that enables the game plan
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        Card golem1 = createCard("Stone Golem", p); // 5 CMC, duplicate
        Card golem2 = createCard("Stone Golem", p); // 5 CMC, duplicate
        Card cheapSpell = createCard("Raging Goblin", p); // 1 CMC, only copy
        hand.add(golem1);
        hand.add(golem2);
        hand.add(cheapSpell);
        hand.add(createCard("Nest Robber", p));
        hand.add(createCard("Nest Robber", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 1);
        assertFalse("Should not return the cheap early-game spell when duplicates exist", result.contains(cheapSpell));
    }

    @Test
    public void testPrefersRetainingLandsWhenOnlyOnePresent() {
        // Only 1 land in 7: that land must be kept at all costs
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        Card onlyLand = createCard("Forest", p);
        hand.add(onlyLand);
        hand.add(createCard("Stone Golem", p));
        hand.add(createCard("Stone Golem", p));
        hand.add(createCard("Verdant Force", p));
        hand.add(createCard("Verdant Force", p));
        hand.add(createCard("Verdant Force", p));
        hand.add(createCard("Raging Goblin", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 3);
        assertFalse("The only land in hand must never be returned", result.contains(onlyLand));
    }

    @Test
    public void testColorMismatchSpellReturnedOverColorMatchSpell() {
        // Hand has two non-land spells: one castable with available lands, one off-color.
        // The off-color spell should be preferred for return.
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        Card onColor  = createCard("Grizzly Bears", p);  // GG: matches forests
        Card offColor = createCard("Mons's Goblin Raiders", p); // R: doesn't match forests
        hand.add(onColor);
        hand.add(offColor);
        hand.add(createCard("Raging Goblin", p));
        hand.add(createCard("Raging Goblin", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 1);
        assertFalse("On-color castable spell should be retained", result.contains(onColor));
    }

    @Test
    public void testScoreCastabilityColorless3PipRequiresThreeSources() {
        // A {3} colorless spell needs enough generic-mana sources; 1 land vs 3 lands matters
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection oneLand = new CardCollection();
        oneLand.add(createCard("Forest", p));

        CardCollection threeLands = new CardCollection();
        threeLands.add(createCard("Forest", p));
        threeLands.add(createCard("Forest", p));
        threeLands.add(createCard("Forest", p));

        Card golem = createCard("Stone Golem", p); // 5 CMC

        byte colors1 = ComputerUtil.getProducibleColorMask(oneLand, p);
        boolean cl1 = ComputerUtil.canProduceColorless(oneLand, p);
        float scoreWith1 = ComputerUtil.scoreCastability(golem, oneLand.size(), colors1, cl1);

        byte colors3 = ComputerUtil.getProducibleColorMask(threeLands, p);
        boolean cl3 = ComputerUtil.canProduceColorless(threeLands, p);
        float scoreWith3 = ComputerUtil.scoreCastability(golem, threeLands.size(), colors3, cl3);

        assertTrue("More lands in hand should yield higher castability score for expensive spell"
                        + " (score1=" + scoreWith1 + ", score3=" + scoreWith3 + ")",
                scoreWith3 > scoreWith1);
    }

    @Test
    public void testLandHeavyHandReturnsExcessLandsBeforeSpells() {
        // 5 lands 2 spells: returning 2 should remove only lands
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 5; i++) {
            hand.add(createCard("Forest", p));
        }
        Card bear  = createCard("Grizzly Bears", p);
        Card goblin = createCard("Llanowar Elves", p);
        hand.add(bear);
        hand.add(goblin);

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertFalse("5-land hand: bear should be retained", result.contains(bear));
        assertFalse("5-land hand: goblin should be retained", result.contains(goblin));
    }

    @Test
    public void testBalancedHandRetainsPlayableCurve() {
        // 3 lands + curve of 1/2/3-drops: a textbook keep.
        // Returning 1 card should remove the highest CMC spell, not a land or cheap spell.
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        hand.add(createCard("Llanowar Elves", p));   // 1 CMC
        hand.add(createCard("Grizzly Bears", p));   // 2 CMC
        hand.add(createCard("Trained Armodon", p)); // 3 CMC
        Card topper = createCard("Stone Golem", p); // 5 CMC: the odd card out

        hand.add(topper);

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 1);
        assertTrue("Balanced hand: the unplayable topper should be returned", result.contains(topper));
    }

    @Test
    public void testSingleLandWithAllCheapSpellsPrefersKeepingLand() {
        // 1 land + 6 cheap spells: we need to return cards; land must stay
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        setupDeck(p);

        CardCollection hand = new CardCollection();
        Card land = createCard("Forest", p);
        hand.add(land);
        for (int i = 0; i < 6; i++) {
            hand.add(createCard("Raging Goblin", p));
        }

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertFalse("The sole land must be kept even when returning 2 cards", result.contains(land));
        assertEquals(2, result.size());
    }

    @Test
    public void testMulticolorCardFullySupported() {
        // Hand has both W and U sources: Azorius card should score 1.0
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Island", p));
        lands.add(createCard("Island", p));
        lands.add(createCard("Island", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card sphinx = createCard("Sphinx of the Revelation", p); // {2}{W}{U}

        float score = ComputerUtil.scoreCastability(sphinx, lands.size(), availableColors, hasColorless);
        assertEquals("WU card with both W and U sources should score 1.0", 1.0f, score, 0.01f);
    }

    @Test
    public void testMulticolorCardMissingOnecolor() {
        // Hand has W but not U: Azorius card should penalise
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));
        lands.add(createCard("Plains", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card sphinx = createCard("Sphinx of the Revelation", p); // {2}{W}{U}

        float score = ComputerUtil.scoreCastability(sphinx, 2, availableColors, hasColorless);
        assertTrue("WU card missing U should score below 0.5, got: " + score, score < 0.2f);
    }

    @Test
    public void testThreecolorCardFullySupported() {
        // Jund hand: B, R, G all present
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Mountain", p));
        lands.add(createCard("Forest", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card card = createCard("Sprouting Thrinax", p); // {B}{R}{G}

        float score = ComputerUtil.scoreCastability(card, 3, availableColors, hasColorless);
        assertEquals("BRG card with all three colors present should score 1.0", 1.0f, score, 0.01f);
    }

    @Test
    public void testThreecolorCardMissingOnecolor() {
        // Jund hand but only B and R: missing G
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Mountain", p));
        lands.add(createCard("Mountain", p));
        lands.add(createCard("Mountain", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card card = createCard("Sprouting Thrinax", p); // {B}{R}{G}

        float score = ComputerUtil.scoreCastability(card, lands.size(), availableColors, hasColorless);
        assertTrue("BRG card missing G should score below 0.5, got: " + score, score < 0.5f);
    }

    @Test
    public void testThreecolorCardMissingTwocolors() {
        // Only have B, missing both R and G
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        CardCollection lands = new CardCollection();
        lands.add(createCard("Swamp", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);
        boolean hasColorless = ComputerUtil.canProduceColorless(lands, p);
        Card card = createCard("Sprouting Thrinax", p); // {B}{R}{G}

        float score = ComputerUtil.scoreCastability(card, 1, availableColors, hasColorless);
        assertTrue("BRG card missing two colors should score near 0, got: " + score, score < 0.2f);
    }

    @Test
    public void testPreferReturningSinglecolorInThreecolorHand() {
        // 3-color hand: we have plenty of one color but lack another
        // Should prefer keeping the cards that address the missing color
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        // Deck has lots of B and G lands, very few R
        for (int i = 0; i < 10; i++) addCardToZone("Swamp", p, ZoneType.Library);
        for (int i = 0; i < 10; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 1;  i++) addCardToZone("Mountain", p, ZoneType.Library);
        for (int i = 0; i < 32; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        // Three redundant black sources
        hand.add(createCard("Swamp", p));
        hand.add(createCard("Swamp", p));
        hand.add(createCard("Swamp", p));
        // One red source: rare, should be kept
        Card mountain = createCard("Mountain", p);
        hand.add(mountain);
        // Spells in BRG
        hand.add(createCard("Sprouting Thrinax", p));
        hand.add(createCard("Sprouting Thrinax", p));
        hand.add(createCard("Sprouting Thrinax", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertFalse("Should not return the scarce red source when black is redundant", result.contains(mountain));
    }

    @Test
    public void testMulticolorHandExcessOncolor() {
        // 4 blue sources, 1 red source, spells need both: excess blue should be returned
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 15; i++) addCardToZone("Island", p, ZoneType.Library);
        for (int i = 0; i < 6;  i++) addCardToZone("Mountain", p, ZoneType.Library);
        for (int i = 0; i < 32; i++) addCardToZone("Raging Goblin", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        Card island1 = createCard("Island", p);
        Card island2 = createCard("Island", p);
        Card island3 = createCard("Island", p);
        Card island4 = createCard("Island", p);
        Card mountain = createCard("Mountain", p);
        hand.add(island1);
        hand.add(island2);
        hand.add(island3);
        hand.add(island4);
        hand.add(mountain);
        hand.add(createCard("Izzet Charm", p));  // {U}{R}
        hand.add(createCard("Izzet Charm", p));

        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 2);
        assertFalse("Should not return the only red source", result.contains(mountain));
        assertTrue("Should return excess blue lands",
                result.contains(island1) || result.contains(island2) ||
                        result.contains(island3) || result.contains(island4));
    }

    @Test
    public void testMissingcolorButDeckCompensates() {
        // Hand has W/U but no G; deck is loaded with Forests: should still keep the multicolor spells
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 18; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 3;  i++) addCardToZone("Plains", p, ZoneType.Library);
        for (int i = 0; i < 32; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Plains", p));
        hand.add(createCard("Plains", p));
        Card bant = createCard("Rafiq of the Many", p); // {2}{G}{W}{U}: pricey, missing G and U
        Card cheap = createCard("Savannah Lions", p);   // {W}: fully castable
        hand.add(bant);
        hand.add(cheap);
        hand.add(createCard("Grizzly Bears", p));
        hand.add(createCard("Grizzly Bears", p));
        hand.add(createCard("Grizzly Bears", p));

        // We're returning 1: the expensive off-color card is riskier than the cheap on-color one
        CardCollectionView result = ComputerUtil.chooseBestCardsToReturn(p, hand, 1);
        assertTrue("Expensive card with missing colors should be preferred for return over cheap on-color card",
                result.contains(bant));
        assertFalse("Cheap on-color card should be kept", result.contains(cheap));
    }

    @Test
    public void testHybridManaFlexibility() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        // Hand has only G, card is {G/W}{G/W}. This should be 100% castable.
        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        hand.add(createCard("Forest", p));
        Card finks = createCard("Kitchen Finks", p); // {1}{G/W}{G/W}

        byte availableColors = ComputerUtil.getProducibleColorMask(hand, p);
        float score = ComputerUtil.scoreCastability(finks, hand.size(), availableColors, false);

        assertEquals("Hybrid card should be fully castable with only one of its colors", 1.0f, score, 0.01f);
    }

    @Test
    public void testLowCmcMissingColorPenalty() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        // Hand has 3 lands: enough total mana for a 3-drop,
        // but only produces Black and Red (Missing Green)
        CardCollection lands = new CardCollection();
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Swamp", p));
        lands.add(createCard("Mountain", p));

        byte availableColors = ComputerUtil.getProducibleColorMask(lands, p);

        // Sprouting Thrinax {B}{R}{G}
        Card thrinax = createCard("Sprouting Thrinax", p);

        float score = ComputerUtil.scoreCastability(thrinax, 3, availableColors, false);

        /* * CURRENT LOGIC CALCULATION:
         * manaRatio = 3/3 = 1.0
         * colorRatio = 2/3 = 0.666
         * score = (1.0^2) * (0.666^3) = 0.296...
         *
         * NEW LOGIC CALCULATION:
         * penalty = 0.1 + (3-1)*0.15 = 0.4
         * score = 0.296 * 0.4 = 0.118...
         */

        assertTrue("A 3-drop missing its color on-curve should be heavily penalized (below 0.15). " +
                        "Current logic returns ~0.296, which is too high. Got: " + score,
                score < 0.15f);
    }

    @Test
    public void testPreferHandWithPlayableEarlyCurve() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        for (int i = 0; i < 20; i++) addCardToZone("Mountain", p, ZoneType.Library);
        for (int i = 0; i < 33; i++) addCardToZone("Lightning Bolt", p, ZoneType.Library);

        // Hand A: 3 lands, spells spread across turns 1, 2, 3
        CardCollection earlyHand = new CardCollection();
        earlyHand.add(createCard("Mountain", p));
        earlyHand.add(createCard("Mountain", p));
        earlyHand.add(createCard("Mountain", p));
        earlyHand.add(createCard("Lightning Bolt", p));  // CMC 1
        earlyHand.add(createCard("Lightning Bolt", p));  // CMC 1
        earlyHand.add(createCard("Goblin Tinkerer", p)); // CMC 2
        earlyHand.add(createCard("Goblin Brawler", p));  // CMC 3

        // Hand B: 3 lands, all spells are CMC 3 — castable, but nothing on turns 1 or 2
        CardCollection lateHand = new CardCollection();
        lateHand.add(createCard("Mountain", p));
        lateHand.add(createCard("Mountain", p));
        lateHand.add(createCard("Mountain", p));
        lateHand.add(createCard("Goblin Brawler", p)); // CMC 3
        lateHand.add(createCard("Goblin Brawler", p)); // CMC 3
        lateHand.add(createCard("Goblin Brawler", p)); // CMC 3
        lateHand.add(createCard("Goblin Brawler", p)); // CMC 3

        int earlyScore = ComputerUtil.scoreHand(earlyHand, p, 0);
        int lateScore  = ComputerUtil.scoreHand(lateHand,  p, 0);

        assertTrue(
                "Hand with plays on turns 1 and 2 should score higher than a hand that can only play on turn 3, " +
                        "but got earlyScore=" + earlyScore + " lateScore=" + lateScore,
                earlyScore > lateScore
        );
    }

    @Test
    public void testHeavySpellDeckAcceptsLowLandHand() {
        // Deck with no lands at all: 0-land hand should not be rejected
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 53; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 7; i++) hand.add(createCard("Grizzly Bears", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertTrue("No-land deck should accept a no-land hand", score > 0);
    }

    @Test
    public void testHeavySpellDeckAcceptsOneLandHand() {
        // Deck with ratio > 6 spells per land: 1-land hand should be accepted
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 8; i++)  addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 52; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);
        // ratio = 60/8 = 7.5 > 6, qualifies as heavy spell deck

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        for (int i = 0; i < 6; i++) hand.add(createCard("Grizzly Bears", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertTrue("Heavy spell deck should accept a 1-land hand", score > 0);
    }

    @Test
    public void testNormalDeckRejectsOneLandHand() {
        // Standard deck (ratio <= 6): 1-land hand should be rejected
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 24; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 36; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        hand.add(createCard("Forest", p));
        for (int i = 0; i < 6; i++) hand.add(createCard("Grizzly Bears", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertEquals("Normal deck should reject a 1-land hand", 0, score);
    }

    @Test
    public void testMomirBasicAcceptsAllLandHand() {
        // Deck with ratio < 2 (nearly all lands): all-land hand should be accepted
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 59; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 1;  i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 7; i++) hand.add(createCard("Forest", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertTrue("Momir Basic / heavy land deck should accept an all-land hand", score > 0);
    }

    @Test
    public void testNormalDeckRejectsAllLandHand() {
        // Standard deck: all-land hand should be rejected
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 24; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 36; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 7; i++) hand.add(createCard("Forest", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertEquals("Normal deck should reject an all-land hand", 0, score);
    }

    @Test
    public void testMomirBasicAcceptsNearFloodHand() {
        // Heavy land deck: 6-land 7-card hand should not be rejected as a flood
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 59; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 1;  i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 6; i++) hand.add(createCard("Forest", p));
        hand.add(createCard("Grizzly Bears", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertTrue("Momir Basic should accept a near-flood hand", score > 0);
    }

    @Test
    public void testNormalDeckRejectsNearFloodHand() {
        // Standard deck: 6-land 7-card hand should be rejected as flooding
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (int i = 0; i < 24; i++) addCardToZone("Forest", p, ZoneType.Library);
        for (int i = 0; i < 36; i++) addCardToZone("Grizzly Bears", p, ZoneType.Library);

        CardCollection hand = new CardCollection();
        for (int i = 0; i < 6; i++) hand.add(createCard("Forest", p));
        hand.add(createCard("Grizzly Bears", p));

        int score = ComputerUtil.scoreHand(hand, p, 0);
        assertEquals("Normal deck should reject a near-flood hand", 0, score);
    }
}