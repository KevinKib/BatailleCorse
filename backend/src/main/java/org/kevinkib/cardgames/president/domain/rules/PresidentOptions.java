package org.kevinkib.cardgames.president.domain.rules;

/**
 * The host options in force, as data: the aggregate reads them and holds no rule switch of its own.
 *
 * <p>Every key of the spec exists here from P1. Only {@code afterPass} and {@code firstLead} change the
 * behaviour of the round rules; the match keys are read from P2 and the variant keys from the PR that
 * ships them (V1 to V4). Until then {@link #defaults()} holds the variant keys at their "off" value,
 * including {@code equalPlay} and {@code forbiddenFinish}, whose Classic default (skip, last place) is set
 * by V1 when the rules exist.
 */
public record PresidentOptions(
        AfterPass afterPass,
        FirstLead firstLead,
        ExchangeAmount exchange,
        GiveBack giveBack,
        NextLead nextLead,
        Scoring scoring,
        MatchLength length,
        EqualPlay equalPlay,
        TwoCuts twoCuts,
        boolean quadClears,
        boolean eightClears,
        ForbiddenFinish forbiddenFinish,
        boolean mustPlay,
        Jokers jokers,
        Revolution revolution,
        Ranking ranking,
        Runs runs,
        boolean suitLock,
        Decks decks,
        boolean putsch,
        boolean presidentFall) {

    public static PresidentOptions defaults() {
        return new PresidentOptions(
                AfterPass.REENTER, FirstLead.LOWEST_CLUB,
                ExchangeAmount.TWO, GiveBack.FREE, NextLead.SCUM, Scoring.RANK, MatchLength.ROUNDS_5,
                EqualPlay.OFF, TwoCuts.OFF, false, false, ForbiddenFinish.OFF, false,
                Jokers.NONE, Revolution.OFF, Ranking.TWO_HIGH,
                Runs.OFF, false,
                Decks.AUTO, false, false);
    }

    public PresidentOptions withAfterPass(AfterPass value) {
        return new PresidentOptions(value, firstLead, exchange, giveBack, nextLead, scoring, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withExchange(ExchangeAmount value) {
        return new PresidentOptions(afterPass, firstLead, value, giveBack, nextLead, scoring, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withGiveBack(GiveBack value) {
        return new PresidentOptions(afterPass, firstLead, exchange, value, nextLead, scoring, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withNextLead(NextLead value) {
        return new PresidentOptions(afterPass, firstLead, exchange, giveBack, value, scoring, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withScoring(Scoring value) {
        return new PresidentOptions(afterPass, firstLead, exchange, giveBack, nextLead, value, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withLength(MatchLength value) {
        return new PresidentOptions(afterPass, firstLead, exchange, giveBack, nextLead, scoring, value, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }

    public PresidentOptions withFirstLead(FirstLead value) {
        return new PresidentOptions(afterPass, value, exchange, giveBack, nextLead, scoring, length, equalPlay,
                twoCuts, quadClears, eightClears, forbiddenFinish, mustPlay, jokers, revolution, ranking, runs,
                suitLock, decks, putsch, presidentFall);
    }
}
