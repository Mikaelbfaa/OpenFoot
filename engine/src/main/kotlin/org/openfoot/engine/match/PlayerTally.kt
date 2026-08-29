package org.openfoot.engine.match

import org.openfoot.model.GoalType
import org.openfoot.model.Half
import org.openfoot.model.PlayerId
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide

/**
 * One player's counters, the whole input section 3.14's rating reads about
 * him.
 *
 * Every field is a plain sum folded out of the log, and none of it is the
 * rating itself: step 3's weights, step 4's defensive comparison, step 5 and
 * step 6's plus terms and the whole minutes penalty all belong to whoever
 * turns this into a number, not to this type. Keeping the two apart is the
 * point, the same reason toStats sits apart from whatever prints a scoreline.
 *
 * matchGoals is not a count of Goal events. It is the sum of every Goal's
 * matchGoalCredits credited to its scorer, which section 3.15 item 13 makes
 * two for an open play, free kick or olympic goal and one for a penalty or an
 * own goal; a player who is on the end of two such goals in one match carries
 * a matchGoals of four here, not two.
 *
 * ownGoals counts a different player from matchGoals for the one goal type
 * that splits the two: an own goal's matchGoalCredits still goes to the
 * attacking finisher who scored it, and ownGoals goes to the defender the
 * report blames instead. See MatchEvent.Goal's own docstring.
 *
 * shotsSavedByKeeper is the shooter's own counter, item 5 of section 3.14 and
 * OPEN-QUESTIONS item 52's confirmed reading: it rises only on the saved
 * branch of section 3.6c's ordinary shot resolution, never on a goal, never on
 * a shot that missed the target, and never on a shot that went through
 * section 3.10's interactive penalty instead, which section 3.10's own
 * InteractivePenaltyResult docstring says feeds a different counter entirely.
 *
 * shotsOnTargetFaced, goalsConceded, missedPenalties and savedPenalties are
 * the four counters only a goalkeeper's own rating reads, section 3.14 step 6.
 * shotsOnTargetFaced and goalsConceded are read off the ordinary Shot and Goal
 * events of whichever man stood in the defending side's goal at the time, so a
 * goalkeeper substituted mid match carries only the share of the match he
 * actually kept, and a keeper who leaves without a replacement leaves nobody
 * carrying whatever happens after him. shotsOnTargetFaced counts a Shot that
 * went through section 3.10's interactive path too, unlike shotsSavedByKeeper
 * above: section 3.10 itself says a converted kick counts as a shot on target
 * and a miss does as well except for the two off target outcomes, so the
 * keeper facing it has faced an on target shot regardless of which path
 * decided the Shot event's own onTarget flag. missedPenalties and
 * savedPenalties come from section 3.10 instead, the taker's own miss and the
 * keeper's own save; see InteractivePenaltyResult's docstring for why the two
 * are distinct counters read by two different clauses of step 6.
 *
 * minutesPlayed is whichever of two figures RuleSet.minutesPlayedRule asks
 * for, and toPlayerTallies below is the only place it is ever set. The event
 * derived figure is section 3.15 item 14's defect and is not a measurement of
 * anything: ninety unless the player appears in the log, and then whichever of
 * a protagonist's or a supporting player's formula his own LAST qualifying
 * event calls for. The actual figure is the time he spent on the pitch, from
 * kick off or from his arrival to his departure or to the final whistle. Both
 * are computed for every player whatever the rule set and the rule chooses
 * between them, so this field carries one number and never both. See
 * OPEN-QUESTIONS item 53.
 */
@SpecRef("3.14")
data class PlayerTally(
    @property:SpecRef("3.15") val matchGoals: Int = 0,
    val ownGoals: Int = 0,
    @property:SpecRef("3.10") val missedPenalties: Int = 0,
    @property:SpecRef("3.10") val savedPenalties: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    @property:SpecRef("3.6") val assists: Int = 0,
    val shotsSavedByKeeper: Int = 0,
    val shotsOnTargetFaced: Int = 0,
    val goalsConceded: Int = 0,
    val minutesPlayed: Int = DEFAULT_MINUTES_PLAYED,
) {
    companion object {

        /**
         * What a player who never appears in section 3.14 step 10's minutes
         * penalty is assumed to have played, since the original never
         * measures time on the pitch at all.
         */
        @SpecRef("3.14")
        const val DEFAULT_MINUTES_PLAYED = 90
    }
}

/**
 * Both sides' counters for one match, keyed the way every per side record in
 * this package is: two maps rather than one keyed by a side and an id
 * together, because a PlayerId is only unique inside its own squad and the
 * home and away squads hand the same numbers out.
 *
 * A player's presence as a key is the whole of section 3.14's "quem recebe
 * nota": both starting elevens and every substitute who came on are keys here,
 * each starting from a PlayerTally of every counter at nought and ninety
 * minutes, and a man who spent the whole match on the bench is not a key at
 * all. Reading a tally back out therefore answers "not rated" and "rated
 * nought" with two different things, a missing key and a present one whose
 * fields happen to be nought, rather than folding the two into one nought
 * meaning both. Nobody is ever removed once entered, so an entry's absence
 * always means the bench and never a player counted and then dropped.
 *
 * Every map is a LinkedHashMap under the hood and never a hash one, in the
 * insertion order toPlayerTallies below builds it: each side's starting eleven
 * in lineup order, then every arrival in the order he came on. Nothing here
 * reads that order for its own sake, the same as SideState's maps, but a
 * reader that walks one for a printed report gets a stable order for free.
 */
@SpecRef("3.14")
data class PlayerTallies(
    val home: Map<PlayerId, PlayerTally> = emptyMap(),
    val away: Map<PlayerId, PlayerTally> = emptyMap(),
) {
    fun of(side: TeamSide): Map<PlayerId, PlayerTally> = if (side == TeamSide.HOME) home else away
}

/**
 * Folds section 3.14's per player counters out of a played match's log.
 *
 * homeLineup and awayLineup must be the two starting elevens, the lineups a
 * match was kicked off with rather than MatchSetup.home.lineup and
 * MatchSetup.away.lineup as they stand at the final whistle. Those two are not
 * the same list once a match has had a single substitution or a single
 * dismissal in it: leavePitch drops a departed man from the lineup outright,
 * so the final lineup is missing every man who left and is carrying every man
 * who arrived in his place. A caller that already has the pre-match MatchSetup
 * it handed to simulateMatch reads the two starting lineups straight off it.
 *
 * rules is read for two fields. keeperSlot is what lets this function
 * recognise the goalkeeper's cell without hard coding the number the classic
 * rules happen to give it. minutesPlayedRule is what settles section 3.15 item
 * 14, below.
 *
 * The fold is single pass over the log except for one lookahead, gathered
 * before the loop starts: which minute and side carried an InteractivePenalty
 * event, which is what tells the loop to withhold shotsSavedByKeeper credit
 * from the Shot event section 3.10's own path replaces rather than adds to.
 * See PlayerTally's own docstring and OPEN-QUESTIONS item 52.
 *
 * Every other rule reads the log exactly once, in order, and never looks
 * ahead or behind: a player's minutesPlayed is simply overwritten every time
 * one of his own events is seen, so the value left standing after the last one
 * is, by construction, the one his last event calls for. Section 3.14's own
 * list of which roles count as protagonist or as supporting is read literally
 * and is not extended, even though its own headline reads "qualquer evento,
 * nao so substituicao": an Injury event on its own, the one case a player can
 * leave the pitch without a Substitution event at all because his side has
 * nobody left to bring on, touches neither role, and neither does a Shot,
 * whether it is on target, wide, saved or scored. Only a Substitution, a
 * Booking, a SendingOff, a scored or missed Goal's author, a Goal's assister
 * and the goalkeeper of a saved InteractivePenalty are named, and this
 * function reads no other event as either for that purpose. The Shot
 * exclusion is the one that actually proves the closed list rather than the
 * wider headline: a Shot is the single most frequent event of a match, so a
 * shooter would be the protagonist of nearly every minute he is ever mentioned
 * in, and item 53's own consequence list, which is entirely about goals, cards
 * and assists, would read nothing like it does if every shot moved a shooter's
 * minutes as well. That is also the argument for Injury, only louder: item 53 lists the
 * events its formula reaches, not every event a player can appear in, and a
 * later change that adds a MatchEvent.Shot branch here would give every
 * shooter in the game a silent minutes penalty that nothing in section 3.14
 * asks for.
 *
 * The actual time on the pitch of section 3.15 item 14 is folded out of the
 * same single pass, into a PitchSpan per player kept beside the tallies, and
 * is turned into a figure only once the whole log has been walked. A starter's
 * span opens at kick off and an arrival's at the minute of his own
 * Substitution; a span closes at the minute of the Substitution that took the
 * man off, of the SendingOff that dismissed him or of the Injury he did not
 * come back from, and a span still open at the end of the log closes at the
 * final whistle, which is the clock's own total and therefore carries that
 * match's stoppage time rather than a flat ninety.
 *
 * Only the FIRST close of a span counts, because an injured man who is
 * replaced produces an Injury and a Substitution in the same minute and the
 * two must not be read as two departures. Every Injury event closes a span,
 * which is not the same list of events as the minutes rule above reads: an
 * Injury moves neither of section 3.14's two formulas and yet always ends the
 * injured man's match, since the branch that logs it either replaces him or
 * drops him from the lineup outright.
 *
 * One departure the log cannot show is the injury whose drawn duration comes
 * to nought days, which only a player of twenty or under can draw. That branch
 * logs no Injury event at all and still takes the player off, so if his side
 * also has nobody to bring on there is nothing in the log to close his span
 * and he is counted to the final whistle. It is recorded here rather than
 * approximated around: the alternative would be a rule invented in this fold
 * about an event that is not there, and the case needs an injury, a player of
 * twenty or under, a duration of nought and an empty bench all at once.
 */
@SpecRef("3.14")
fun List<MatchEvent>.toPlayerTallies(
    homeLineup: List<MatchPlayer>,
    awayLineup: List<MatchPlayer>,
    rules: RuleSet,
    clock: MatchClock,
): PlayerTallies {
    val home = LinkedHashMap<PlayerId, PlayerTally>()
    val away = LinkedHashMap<PlayerId, PlayerTally>()
    val homeSpans = LinkedHashMap<PlayerId, PitchSpan>()
    val awaySpans = LinkedHashMap<PlayerId, PitchSpan>()
    for (player in homeLineup) {
        home[player.id] = PlayerTally()
        homeSpans[player.id] = PitchSpan(arrived = KICK_OFF_MINUTE)
    }
    for (player in awayLineup) {
        away[player.id] = PlayerTally()
        awaySpans[player.id] = PitchSpan(arrived = KICK_OFF_MINUTE)
    }

    fun tallyOf(side: TeamSide): LinkedHashMap<PlayerId, PlayerTally> = if (side == TeamSide.HOME) home else away

    fun spansOf(side: TeamSide): LinkedHashMap<PlayerId, PitchSpan> =
        if (side == TeamSide.HOME) homeSpans else awaySpans

    fun arrive(side: TeamSide, id: PlayerId, minute: Int) {
        spansOf(side)[id] = PitchSpan(arrived = minute)
    }

    fun depart(side: TeamSide, id: PlayerId, minute: Int) {
        val spans = spansOf(side)
        val current = checkNotNull(spans[id]) {
            "$id of $side has no span yet, and only a starter or a Substitution arrival should have one"
        }
        if (current.left == null) {
            spans[id] = current.copy(left = minute)
        }
    }

    fun touch(side: TeamSide, id: PlayerId, change: (PlayerTally) -> PlayerTally) {
        val map = tallyOf(side)
        val current = checkNotNull(map[id]) {
            "$id of $side has no tally yet, and only a starter or a Substitution arrival should have one"
        }
        map[id] = change(current)
    }

    fun protagonistMinute(minute: Int): Int {
        val intoHalf = clock.intoHalf(minute)
        return if (clock.halfOf(minute) == Half.FIRST) intoHalf else PROTAGONIST_SECOND_HALF_OFFSET + intoHalf
    }

    fun supportingMinute(minute: Int): Int {
        val intoHalf = clock.intoHalf(minute)
        return if (clock.halfOf(minute) == Half.FIRST) {
            SUPPORTING_FIRST_HALF_BASE - intoHalf
        } else {
            SUPPORTING_SECOND_HALF_BASE - intoHalf
        }
    }

    var homeKeeper: PlayerId? = homeLineup.firstOrNull { it.slot.value == rules.keeperSlot }?.id
    var awayKeeper: PlayerId? = awayLineup.firstOrNull { it.slot.value == rules.keeperSlot }?.id

    fun keeperOf(side: TeamSide): PlayerId? = if (side == TeamSide.HOME) homeKeeper else awayKeeper

    fun setKeeperOf(side: TeamSide, id: PlayerId?) {
        if (side == TeamSide.HOME) homeKeeper = id else awayKeeper = id
    }

    val interactivePenaltyMinutes = filterIsInstance<MatchEvent.InteractivePenalty>()
        .map { it.minute to it.side }
        .toSet()

    for (event in this) {
        when (event) {
            is MatchEvent.Goal -> {
                val authorSide = if (event.type == GoalType.OWN_GOAL) event.side.opponent else event.side
                if (event.author != null) {
                    touch(authorSide, event.author.id) {
                        it.copy(
                            ownGoals = it.ownGoals + if (event.type == GoalType.OWN_GOAL) 1 else 0,
                            minutesPlayed = protagonistMinute(event.minute),
                        )
                    }
                }
                if (event.scorer != null) {
                    touch(event.side, event.scorer.id) {
                        it.copy(matchGoals = it.matchGoals + event.matchGoalCredits)
                    }
                }
                if (event.assister != null) {
                    touch(event.side, event.assister.id) {
                        it.copy(assists = it.assists + 1, minutesPlayed = supportingMinute(event.minute))
                    }
                }
                val concedingSide = event.side.opponent
                keeperOf(concedingSide)?.let { keeper ->
                    touch(concedingSide, keeper) { it.copy(goalsConceded = it.goalsConceded + 1) }
                }
            }

            is MatchEvent.Shot -> {
                val throughInteractivePenalty = (event.minute to event.side) in interactivePenaltyMinutes
                if (event.shooter != null && event.onTarget && !event.scored && !throughInteractivePenalty) {
                    touch(event.side, event.shooter.id) {
                        it.copy(shotsSavedByKeeper = it.shotsSavedByKeeper + 1)
                    }
                }
                if (event.onTarget) {
                    val concedingSide = event.side.opponent
                    keeperOf(concedingSide)?.let { keeper ->
                        touch(concedingSide, keeper) { it.copy(shotsOnTargetFaced = it.shotsOnTargetFaced + 1) }
                    }
                }
            }

            is MatchEvent.InteractivePenalty -> {
                if (event.taker != null && !event.scored) {
                    touch(event.side, event.taker.id) { it.copy(missedPenalties = it.missedPenalties + 1) }
                }
                if (event.keeper != null && event.keeperSaved) {
                    touch(event.side.opponent, event.keeper.id) {
                        it.copy(
                            savedPenalties = it.savedPenalties + 1,
                            minutesPlayed = supportingMinute(event.minute),
                        )
                    }
                }
            }

            is MatchEvent.Booking ->
                touch(event.side, event.player.id) {
                    it.copy(yellowCards = it.yellowCards + 1, minutesPlayed = protagonistMinute(event.minute))
                }

            is MatchEvent.SendingOff -> {
                touch(event.side, event.player.id) {
                    it.copy(redCards = it.redCards + 1, minutesPlayed = protagonistMinute(event.minute))
                }
                depart(event.side, event.player.id, event.minute)
                if (event.player.slot.value == rules.keeperSlot) {
                    setKeeperOf(event.side, null)
                }
            }

            is MatchEvent.Injury -> {
                depart(event.side, event.player.id, event.minute)
                if (event.player.slot.value == rules.keeperSlot) {
                    setKeeperOf(event.side, null)
                }
            }

            is MatchEvent.Substitution -> {
                touch(event.side, event.off.id) {
                    it.copy(minutesPlayed = protagonistMinute(event.minute))
                }
                depart(event.side, event.off.id, event.minute)
                val onSide = tallyOf(event.side)
                val arriving = onSide[event.on.id] ?: PlayerTally()
                onSide[event.on.id] = arriving.copy(minutesPlayed = supportingMinute(event.minute))
                arrive(event.side, event.on.id, event.minute)
                if (event.on.slot.value == rules.keeperSlot) {
                    setKeeperOf(event.side, event.on.id)
                }
            }

            is MatchEvent.Tackle, is MatchEvent.MisplacedPass, is MatchEvent.PossessionWon -> Unit
        }
    }

    fun settleMinutes(tallies: LinkedHashMap<PlayerId, PlayerTally>, spans: Map<PlayerId, PitchSpan>) {
        for (id in tallies.keys.toList()) {
            val tally = tallies.getValue(id)
            val span = spans.getValue(id)
            val actual = (span.left ?: clock.totalMinutes) - span.arrived
            tallies[id] = tally.copy(
                minutesPlayed = rules.minutesPlayedRule.minutes(tally.minutesPlayed, actual),
            )
        }
    }

    settleMinutes(home, homeSpans)
    settleMinutes(away, awaySpans)

    return PlayerTallies(home = home, away = away)
}

/**
 * When one player reached the pitch and when he left it, in whole match
 * minutes counted from kick off.
 *
 * left is null for as long as the log has not shown him leaving, which at the
 * end of the walk means he was still on at the final whistle. It is not a
 * sentinel for an unknown departure: the one departure the log genuinely
 * cannot show is argued for on toPlayerTallies above, and it too is read as
 * still on.
 *
 * Held beside the tallies rather than on PlayerTally because neither figure is
 * a counter section 3.14 reads. What section 3.14 reads is the one number the
 * two of them, and the rule set, produce.
 */
@SpecRef("3.15")
private data class PitchSpan(val arrived: Int, val left: Int? = null)

/** The minute a starter's time on the pitch begins, counted from nought. */
@SpecRef("3.15")
private const val KICK_OFF_MINUTE = 0

/** Section 3.14's second half offset for a protagonist's minute. */
@SpecRef("3.14")
private const val PROTAGONIST_SECOND_HALF_OFFSET = 48

/** Section 3.14's first half base a supporting player's minute counts down from. */
@SpecRef("3.14")
private const val SUPPORTING_FIRST_HALF_BASE = 98

/** Section 3.14's second half base a supporting player's minute counts down from. */
@SpecRef("3.14")
private const val SUPPORTING_SECOND_HALF_BASE = 50
