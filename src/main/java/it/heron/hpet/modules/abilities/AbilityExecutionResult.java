package it.heron.hpet.modules.abilities;

record AbilityExecutionResult(Runnable cleanup, long cleanupAfterMillis) {

    private static final Runnable NO_CLEANUP = () -> { };

    static AbilityExecutionResult immediate() {
        return new AbilityExecutionResult(NO_CLEANUP, -1L);
    }

    static AbilityExecutionResult persistent(Runnable cleanup) {
        return new AbilityExecutionResult(cleanup, 0L);
    }

    static AbilityExecutionResult temporary(long durationMillis, Runnable cleanup) {
        return new AbilityExecutionResult(cleanup, durationMillis);
    }
}
