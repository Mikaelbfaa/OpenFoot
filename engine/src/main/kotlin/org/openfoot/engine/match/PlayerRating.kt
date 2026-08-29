package org.openfoot.engine.match

import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.RatingLimits
import org.openfoot.model.RatingRules
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.Slot
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.Trait
import org.openfoot.model.pick
import org.openfoot.model.rand

/**
 * One player's mark for one match, on the same nought to ten scale the
 * original shows.
 *
 * value carries a genuine nought where section 3.14 step 11 asks for one:
 * a player who came on for fewer than twenty minutes and whose rating came to
 * rest exactly on the floor is given nought, which the original means as
 * "no rating at all". That is not the same fact as a substitute who never
 * came on, who is not rated at all and has no entry in MatchRatings below, so
 * the two are never folded into one nought.
 *
 * slot is the cell the rating was computed for, and it is carried because it
 * is not always the cell the player walked out in. Section 3.14 substitutes a
 * default cell for a player whose own is nought or less and WRITES that value
 * back onto the player, so it outlives the rating in the original. Nothing in
 * this engine mutates a MatchPlayer, so the substituted cell is reported here
 * instead of stored on him; a caller that keeps a player's cell across matches
 * is the one that has to write it back. For every player who walked out in a
 * cell of his own this is simply that cell.
 */
@SpecRef("3.14")
data class PlayerRating(
    val value: Double,
    val slot: Slot,
)

/**
 * Both sides' marks for one match, keyed the way PlayerTallies and every
 * other per side record in this package is: two maps rather than one keyed by
 * a side and an identity together, because a PlayerId is unique only inside
 * its own squad.
 *
 * A player's presence as a key is the whole of section 3.14's "quem recebe
 * nota". Both starting elevens and every substitute who came on are keys; a
 * man who spent the match on the bench is not a key at all. Reading a mark
 * back therefore answers "not rated" with a missing key and "rated nought"
 * with a present entry whose value is nought, and those are two different
 * facts about two different players.
 *
 * Both maps are ordered, in the order PlayerTallies was built in: each side's
 * starting eleven in lineup order, then every arrival in the order he came on.
 */
@SpecRef("3.14")
data class MatchRatings(
    val home: Map<PlayerId, PlayerRating> = emptyMap(),
    val away: Map<PlayerId, PlayerRating> = emptyMap(),
) {
    fun of(side: TeamSide): Map<PlayerId, PlayerRating> = if (side == TeamSide.HOME) home else away
}

/**
 * What one side's whole match was, as far as a rating is concerned.
 *
 * Three of section 3.14's eleven steps read the match rather than the player:
 * the base table reads the result, step 2 reads the two possession counters
 * and step 4 reads the two tackle counters, while step 7 reads how many goals
 * the side let in. Every one of those is the same for all eleven men, so it
 * is gathered once per side rather than passed as six loose arguments to each
 * player in turn.
 *
 * The two comparisons are held as the raw pair of counters rather than as a
 * decided winner, so that the rule for a level count, which is that neither
 * clause fires and no draw is spent, lives in exactly one place below.
 *
 * goalsAgainst does double duty and deliberately: the base table reads it
 * against goalsFor to name the result, and step 7 reads it on its own. It is
 * the side's goals conceded and not a goalkeeper's, which is a different
 * counter that step 6 reads off the tally: a keeper substituted at the
 * interval carries only his own share, while step 7 charges every defender
 * for the whole match.
 *
 * opponentShots is every shot the other side took, on target or not, and is
 * the one figure step 6 reads about the match rather than about the
 * goalkeeper. Section 3.14 says "se o adversario chutou" for that tier where
 * it says "chute no alvo sofrido" for the tier above it, and section 3.15
 * item 16 calls the two unreachable tiers stacked on top of it chutes
 * sofridos, so it is the total and not the on target count. It is a side wide
 * figure for the same reason the two comparisons above are, and it is
 * deliberately not the goalkeeper's own shotsOnTargetFaced: the two are
 * different numbers in every match, and reading the smaller of them would
 * silently withhold the bonus from nearly every goalkeeper in the game.
 */
@SpecRef("3.14")
internal data class SideRatingContext(
    val goalsFor: Int,
    val goalsAgainst: Int,
    val possessionsWon: Int,
    val opponentPossessionsWon: Int,
    val tackles: Int,
    val opponentTackles: Int,
    val opponentShots: Int,
)

/**
 * Section 3.14's post match rating for everybody who played.
 *
 * The report is the input rather than a MatchSetup, because a setup at the
 * final whistle no longer holds the two starting elevens; see MatchReport's
 * own docstring on why the two lineups are carried there.
 *
 * rng is the seed source handed to simulateMatch and not a stream, exactly as
 * that function documents its own argument: this forks it at SeedDomain.MATCH
 * and then at RATING_STREAM, which is where the ratings of that match live, so
 * simulating a match and rating it are two calls made from the same seed and
 * both replay. Passing a generator that has already produced values changes
 * nothing, since a fork depends only on the origin seed and the tag.
 *
 * A rating feeds nothing back into the match. Nothing here is read by any
 * minute of play, so a match's clock, goals and statistics are exactly what
 * they were before section 3.14 existed.
 */
@SpecRef("3.14")
fun MatchReport.playerRatings(rules: RuleSet, rng: Rng): MatchRatings {
    val tallies = log.toPlayerTallies(homeLineup, awayLineup, rules, clock)
    val ratingRng = rng.fork(SeedDomain.MATCH).fork(RATING_STREAM)
    return MatchRatings(
        home = ratingsOf(TeamSide.HOME, tallies.home, contextOf(TeamSide.HOME), rules, ratingRng),
        away = ratingsOf(TeamSide.AWAY, tallies.away, contextOf(TeamSide.AWAY), rules, ratingRng),
    )
}

/**
 * One side's match as its own players see it.
 *
 * The possession counter is the duel counter of section 3.13 and not a share
 * of the ticks, since possession alternates unconditionally and that share is
 * always one half; it is the same number the original displays as possession
 * per cent, which is what section 3.14 says it compares.
 */
@SpecRef("3.14")
private fun MatchReport.contextOf(side: TeamSide): SideRatingContext {
    val own = stats.of(side)
    val other = stats.of(side.opponent)
    return SideRatingContext(
        goalsFor = if (side == TeamSide.HOME) homeGoals else awayGoals,
        goalsAgainst = if (side == TeamSide.HOME) awayGoals else homeGoals,
        possessionsWon = own.possessionsWon,
        opponentPossessionsWon = other.possessionsWon,
        tackles = own.tackles,
        opponentTackles = other.tackles,
        opponentShots = other.shots,
    )
}

/**
 * Rates one side, walking the tallies in the order they were built.
 *
 * Every player draws from a stream of his own, forked from the side's and
 * tagged with his identity. Section 3.14 spends a different number of draws
 * on different players, since every chance in it sits behind a slot band or
 * behind a comparison, so a single shared stream would have made one man's
 * rating depend on the cell of whoever was rated before him. Tagging by
 * identity rather than by position in the list goes one step further: a
 * change to the order the eleven are listed in cannot move anybody's mark
 * either.
 */
@SpecRef("3.14")
private fun MatchReport.ratingsOf(
    side: TeamSide,
    tallies: Map<PlayerId, PlayerTally>,
    context: SideRatingContext,
    rules: RuleSet,
    rng: Rng,
): Map<PlayerId, PlayerRating> {
    val players = ratedPlayers(side)
    val sideRng = rng.fork(side.ordinal.toLong())
    val rated = LinkedHashMap<PlayerId, PlayerRating>()
    for ((id, tally) in tallies) {
        val player = checkNotNull(players[id]) {
            "$id of $side has a tally but no player, and only a starter or a substitution arrival " +
                "can have either"
        }
        rated[id] = ratePlayer(player, tally, context, rules, sideRng.fork(id.value.toLong()))
    }
    return rated
}

/**
 * Everybody of one side who section 3.14 rates, by identity.
 *
 * The starting eleven comes off the report and every arrival comes off the
 * log's own substitution events, which is the only record of a man who was
 * not named at kick off. The arriving object is the one to keep rather than
 * the bench object it was built from: he is standing in the cell he inherited
 * and MatchEvent.Substitution's own docstring says so, and every slot band of
 * section 3.14 reads that cell.
 *
 * A man who came on and was later taken off again is still one entry, since
 * he can only arrive once.
 */
@SpecRef("3.14")
private fun MatchReport.ratedPlayers(side: TeamSide): Map<PlayerId, MatchPlayer> {
    val players = LinkedHashMap<PlayerId, MatchPlayer>()
    for (player in if (side == TeamSide.HOME) homeLineup else awayLineup) {
        players[player.id] = player
    }
    for (event in log) {
        if (event is MatchEvent.Substitution && event.side == side) {
            players[event.on.id] = event.on
        }
    }
    return players
}

/**
 * Section 3.14's eleven steps, in the one order that produces its numbers.
 *
 * The order is the content of this function and four of the steps do not
 * commute with each other. Step 9 caps at ten and then turns a negative into
 * one; step 10 charges the minutes penalty; step 11 floors at two and then
 * zeroes a short appearance that landed on the floor. Applying the floor
 * before the minutes penalty would let a short appearance finish below two;
 * applying the cap after step 8 rather than before it is the difference
 * between a red star finishing at ten and finishing at ten and a half; and
 * turning a negative into two rather than into one, or into nought, changes
 * every rating that a dismissal and a bad match together drove under nought.
 *
 * The base is picked by the player's own strength, not by his effective
 * strength of section 3.3 and not by his side's level, and it is picked
 * before every adjustment because every adjustment is stated as an addition
 * to it.
 *
 * Internal rather than private so a test can hand it one hand built tally and
 * pin one step at a time, which is the only way to prove an eleven step order
 * rather than the sum it happens to produce.
 */
@SpecRef("3.14")
internal fun ratePlayer(
    player: MatchPlayer,
    tally: PlayerTally,
    context: SideRatingContext,
    rules: RuleSet,
    rng: Rng,
): PlayerRating {
    val ratings = rules.ratings
    val limits = ratings.limits
    val slot = effectiveSlot(player, ratings)

    var value = ratings.base.pick(player.strength).forResult(context.goalsFor, context.goalsAgainst)
    value += outOfPositionAdjustment(player, slot, rules)
    value += midfieldAdjustment(player, slot, context, ratings, rng)
    value += eventAdjustment(tally, ratings)
    value += defensiveAdjustment(slot, context, ratings, rng)
    value += tally.shotsSavedByKeeper * ratings.savedShotBonus
    value += keeperAdjustment(slot, tally, context, rules)
    value += cleanSheetAdjustment(slot, context, ratings, rng)
    value += starAdjustment(player, ratings)

    value = minOf(value, limits.cap)
    if (value < 0.0) {
        value = limits.negativeReplacement
    }
    value += minutesAdjustment(tally.minutesPlayed, limits)
    value = maxOf(value, limits.floor)
    if (tally.minutesPlayed < limits.noRatingMinutes && value == limits.floor) {
        value = limits.noRating
    }

    return PlayerRating(value = value, slot = slot)
}

/**
 * The cell section 3.14 rates the player in.
 *
 * A cell of nought or less is not a cell: the original has nought for a
 * player who was never given one and minus one for an unused substitute, and
 * neither can be looked up in a band. Section 3.14 substitutes a default by
 * natural position and writes it back onto the player, so every band below
 * reads the substituted value and never the nought. Step 1 is the one place
 * that still remembers there was no cell, which is why it asks the player
 * rather than asking this.
 */
@SpecRef("3.14")
private fun effectiveSlot(player: MatchPlayer, ratings: RatingRules): Slot =
    if (player.slot.value <= NO_SLOT_AT_MOST) {
        Slot(ratings.defaultSlotFor(player.naturalPosition))
    } else {
        player.slot
    }

/**
 * Step 1. Out of position, and worse still in goal.
 *
 * Two different men reach the second penalty. One is an outfield player
 * standing in the goalkeeper's cell, which is what the step is written for.
 * The other is a goalkeeper who had no cell at all: his default by natural
 * position IS the goalkeeper's cell, and having had no cell counts as out of
 * position in its own right, so he collects the first penalty for a cell he
 * suits perfectly and the second for the cell he was given.
 */
@SpecRef("3.14")
private fun outOfPositionAdjustment(player: MatchPlayer, slot: Slot, rules: RuleSet): Double {
    val hadNoSlot = player.slot.value <= NO_SLOT_AT_MOST
    if (!hadNoSlot && player.naturalPosition == slot.requiredPosition) {
        return 0.0
    }
    val ratings = rules.ratings
    var adjustment = ratings.outOfPositionPenalty
    if (slot.value == rules.keeperSlot) {
        adjustment += ratings.outOfPositionInGoalPenalty
    }
    return adjustment
}

/**
 * Step 2. What the two possession counters were worth to a midfield cell.
 *
 * The chance pays LESS here, which is the opposite of step 4 below: the side
 * with the better counter takes the full bonus two times in three and the
 * reduced one otherwise, and the side with the worse counter is charged the
 * full penalty two times in three and the reduced one otherwise. Level
 * counters fire neither clause and spend no draw at all.
 *
 * The term the old wording called the volante bonus tests ONE thing: the
 * derived sub role of section 4.3 standing at defensive. The cell has already
 * been checked by the band above and is not consulted again, and the player's
 * natural position is not consulted at all. Section 3.14 names a slot range
 * in steps 4 and 7 and names a derived style here, which is why this is the
 * only clause of the three that asks the player rather than the cell.
 *
 * Three consequences follow, and only the shape above gets all three right.
 * An attacking midfielder with a defensive style standing at 14, 15 or 16
 * collects it, because the band is the whole of what the cell decides. A
 * playmaker standing at 12 does not, because a holding cell demands no
 * particular style of whoever fills it and section 5.4's third relaxation
 * pass gives it up altogether. And in the two wing back cells, 10 and 17,
 * which section 3.2 makes demand a fullback, the man who collects it is a
 * FULLBACK with a defensive style: there is no midfielder standing there to
 * collect anything, so a test that also asked for a midfielder would exclude
 * the only player who can ever qualify and the term would never pay in those
 * two cells at all. See OPEN-QUESTIONS item 60.
 *
 * The characteristic bonus reads the FIRST characteristic only, and only on
 * the winning side of the comparison. A player with Armacao second earns
 * exactly what a player with neither earns.
 */
@SpecRef("3.14")
private fun midfieldAdjustment(
    player: MatchPlayer,
    slot: Slot,
    context: SideRatingContext,
    ratings: RatingRules,
    rng: Rng,
): Double {
    val midfield = ratings.midfield
    if (slot.value !in midfield.slots) {
        return 0.0
    }
    val defensiveStyle = player.style == PlayerStyle.DEFENSIVE
    var adjustment = 0.0
    when {
        context.possessionsWon > context.opponentPossessionsWon -> {
            adjustment += if (rng.chanceHits(ratings.thirdChanceIn)) {
                midfield.moreReducedBonus
            } else {
                midfield.moreBonus
            }
            if (defensiveStyle) {
                adjustment += midfield.holdingMoreBonus
            }
            if (player.firstTrait == Trait.PASSING || player.firstTrait == Trait.PLAYMAKING) {
                adjustment += midfield.firstTraitBonus
            }
        }

        context.possessionsWon < context.opponentPossessionsWon -> {
            adjustment += if (rng.chanceHits(ratings.thirdChanceIn)) {
                midfield.lessReducedPenalty
            } else {
                midfield.lessPenalty
            }
            if (defensiveStyle) {
                adjustment += midfield.holdingLessPenalty
            }
        }
    }
    return adjustment
}

/**
 * Step 3. The player's own counters, every one of them a multiplier.
 *
 * The goal term reads the match goal counter of section 3.15 item 13, which
 * an open play, free kick or olympic goal moves twice, so one open play goal
 * is worth one point eight here and not nought point nine.
 *
 * The missed penalty term is section 3.15 item 15 and is reproduced with its
 * defect intact. It is switched on by the player having missed at least one
 * interactive penalty and then multiplies his OWN GOALS, not his missed
 * penalties. A player who missed a penalty and scored no own goal loses
 * nothing; a player who did both in one match is charged for the own goal
 * twice, once at one point five and again at one point two. Repairing it is
 * explicitly out of scope and no rule set switches it off, so there is no
 * branch here for the intended reading.
 */
@SpecRef("3.14")
private fun eventAdjustment(tally: PlayerTally, ratings: RatingRules): Double {
    val events = ratings.events
    var adjustment = tally.matchGoals * events.goalBonus
    adjustment += tally.ownGoals * events.ownGoalPenalty
    if (tally.missedPenalties > 0) {
        adjustment += tally.ownGoals * events.missedPenaltyOwnGoalPenalty
    }
    adjustment += tally.yellowCards * events.yellowCardPenalty
    adjustment += tally.redCards * events.redCardPenalty
    adjustment += tally.assists * events.assistBonus
    return adjustment
}

/**
 * Step 4. What the two tackle counters were worth to a defensive cell.
 *
 * The chance pays MORE here, the opposite of step 2 above: the side with the
 * better counter takes the smaller figure two times in three and the larger
 * one otherwise.
 *
 * The rewarded band and the punished band are deliberately different and are
 * not two readings of one range. The reward reaches every defensive cell from
 * the first fullback to the last, laterais included; the punishment covers
 * the six centre back cells alone. A lateral is therefore paid for a side
 * that wins the tackle count and charged nothing at all for a side that loses
 * it. The holding cells are paid and charged on both sides, and their two
 * chances are a third and a quarter respectively, so even they are not
 * symmetric.
 *
 * Each banded term draws only for a player whose cell is in the band, so how
 * many draws a rating spends depends on where the man was standing. Every
 * player rating from a stream of his own is what keeps that from mattering.
 */
@SpecRef("3.14")
private fun defensiveAdjustment(
    slot: Slot,
    context: SideRatingContext,
    ratings: RatingRules,
    rng: Rng,
): Double {
    val defending = ratings.defending
    if (slot.value !in defending.slots) {
        return 0.0
    }
    var adjustment = 0.0
    when {
        context.tackles > context.opponentTackles -> {
            adjustment += if (rng.chanceHits(ratings.thirdChanceIn)) {
                defending.moreRaisedBonus
            } else {
                defending.moreBonus
            }
            if (slot.value in defending.rewardSlots && rng.chanceHits(ratings.thirdChanceIn)) {
                adjustment += defending.rewardBonus
            }
            if (slot.value in defending.holdingSlots && rng.chanceHits(ratings.thirdChanceIn)) {
                adjustment += defending.holdingBonus
            }
        }

        context.tackles < context.opponentTackles -> {
            adjustment += defending.lessPenalty
            if (slot.value in defending.punishSlots && rng.chanceHits(ratings.quarterChanceIn)) {
                adjustment += defending.punishPenalty
            }
            if (slot.value in defending.holdingSlots && rng.chanceHits(ratings.quarterChanceIn)) {
                adjustment += defending.holdingPenalty
            }
        }
    }
    return adjustment
}

/**
 * Step 6. The goalkeeper's own clause, which nobody else reaches.
 *
 * The busy bonus is the only term of the whole step that reads the match
 * rather than the goalkeeper, and it counts EVERY shot the other side took
 * and not only the ones on target. Section 3.14's own sentence changes
 * wording for it, and section 3.15 item 16 calls the two tiers above it
 * chutes sofridos; reading the keeper's on target counter here instead would
 * compare a number two thirds smaller against the same threshold and would
 * quietly withhold the bonus from nearly every goalkeeper in the game.
 *
 * It is also the only rung of its chain that can ever fire. The original has
 * two more above it, at more than fifteen and more than twenty shots, sitting
 * behind this one in an else chain that no count can get past; section 3.15
 * item 16 says so and says not to port them, and there is nothing here for
 * them to be ported into.
 *
 * The goals conceded chain is read as a chain and not as a partition, so
 * three goals conceded costs what two costs. There is no rung for three.
 *
 * The last penalty is charged on top of the clean sheet reward the chain has
 * already paid, so a goalkeeper who faced nothing at all finishes this step
 * below where he started rather than above it.
 */
@SpecRef("3.14")
private fun keeperAdjustment(
    slot: Slot,
    tally: PlayerTally,
    context: SideRatingContext,
    rules: RuleSet,
): Double {
    if (slot.value != rules.keeperSlot) {
        return 0.0
    }
    val keeper = rules.ratings.keeper
    var adjustment = keeper.basePenalty
    adjustment += tally.shotsOnTargetFaced * keeper.shotOnTargetBonus
    adjustment += tally.savedPenalties * keeper.savedPenaltyBonus
    if (context.opponentShots > keeper.busyShotsAbove) {
        adjustment += keeper.busyBonus
    }
    adjustment += keeper.conceded.pick(tally.goalsConceded)
    if (tally.shotsOnTargetFaced == 0) {
        adjustment += keeper.noShotsFacedPenalty
    }
    return adjustment
}

/**
 * Step 7. The scoreline, charged to the defensive cells, and the tax.
 *
 * The goals conceded here are the side's own and not the goalkeeper's: a
 * defender is charged for every goal the side let in, whichever keeper was
 * behind him.
 *
 * Conceding exactly one goal costs nothing at all. The per goal charge starts
 * at two conceded and then applies to every goal including the first, so two
 * goals cost two of them and not one.
 *
 * The tax is the strangest term in the section and is deliberately outside
 * both clauses above. It is charged on a chance of one in three to every
 * defensive cell from the second onwards, on nothing: not on the scoreline,
 * not on the tackle count, not on anything the player did. It is the reason a
 * defender's rating for two identical matches can differ by nought point
 * four with no event between them to explain it.
 */
@SpecRef("3.14")
private fun cleanSheetAdjustment(
    slot: Slot,
    context: SideRatingContext,
    ratings: RatingRules,
    rng: Rng,
): Double {
    val clean = ratings.cleanSheet
    if (slot.value !in clean.slots) {
        return 0.0
    }
    var adjustment = 0.0
    if (context.goalsAgainst == 0) {
        adjustment += clean.bonus
        if (slot.value in clean.rewardSlots) {
            adjustment += clean.rewardBonus
        }
        if (slot.value in clean.holdingSlots && rng.chanceHits(ratings.thirdChanceIn)) {
            adjustment += clean.holdingBonus
        }
    } else if (context.goalsAgainst >= clean.concededFrom) {
        adjustment += context.goalsAgainst * clean.concededPenaltyPerGoal
    }
    if (slot.value in clean.taxedSlots && rng.chanceHits(ratings.thirdChanceIn)) {
        adjustment += clean.taxPenalty
    }
    return adjustment
}

/**
 * Step 8. The two badges of section 4.10, which are cumulative.
 *
 * The two are independent sums and not a chain, so a player carrying both is
 * a full point up. That is the same reading section 4.9's market value and
 * section 4.8's salary take of the same table, where the red star multiplier
 * is applied on top of the plain star one and not instead of it.
 *
 * A red star is not always a plain star as well, which is why the two flags
 * are read separately here rather than one derived from the other. Section
 * 4.10's implication holds only where a badge arrives from outside, at world
 * creation and when a squad file is read; the end of season promotion to red
 * star lights the red flag alone and leaves the common one off. A promoted
 * player therefore collects nought point six where an identical player who
 * came from a file collects a full point, which is section 3.15 item 18 and
 * OPEN-QUESTIONS item 62. Nothing here can cause or repair that, since it is
 * decided by season turnover, but it is why two red starred players can be
 * worth different amounts in this step.
 */
@SpecRef("3.14")
private fun starAdjustment(player: MatchPlayer, ratings: RatingRules): Double {
    var adjustment = 0.0
    if (player.star) {
        adjustment += ratings.starBonus
    }
    if (player.topWorld) {
        adjustment += ratings.topWorldBonus
    }
    return adjustment
}

/**
 * Step 10. The minutes penalty, whose two rungs are exclusive.
 *
 * Minutes played are not a measurement of time on the pitch. They are ninety
 * unless the player appeared in an event, and then they are whatever the
 * minute of his LAST event works out to, which is why a striker who scored
 * early in the first half is charged here as though he had barely played. See
 * PlayerTally and OPEN-QUESTIONS item 53.
 */
@SpecRef("3.14")
private fun minutesAdjustment(minutes: Int, limits: RatingLimits): Double = when {
    minutes < limits.shortMinutes -> limits.shortPenalty
    minutes < limits.partialMinutes -> limits.partialPenalty
    else -> 0.0
}

/**
 * Whether a one in N chance came up.
 *
 * Section 3.14 states its chances as probabilities and never as a comparison
 * against a drawn number, unlike section 3.8, so nought is as faithful a
 * transcription as one and is the plainer of the two under a rand that
 * returns nought to N minus one.
 */
@SpecRef("3.14")
private fun Rng.chanceHits(inN: Int): Boolean = rand(inN) == CHANCE_HITS

/** The draw that means a one in N chance came up. */
@SpecRef("3.14")
private const val CHANCE_HITS = 0

/**
 * The largest cell number that is not a cell at all.
 *
 * Nought is a player who was never given one and minus one is an unused
 * substitute, and section 3.14 treats both the same way.
 */
@SpecRef("3.14")
private const val NO_SLOT_AT_MOST = 0
