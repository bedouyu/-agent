package com.paperagent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LatexSafetyGuardTest {

    private final LatexSafetyGuard guard = new LatexSafetyGuard();

    @Test
    void unchangedReferencesAndMathDelimitersShouldPass() {
        assertThat(guard.inspect("见 \\cite{a}，设 $x=1$。", "由 \\cite{a} 可知，$x=1$。"))
                .isEmpty();
    }

    @Test
    void changedCitationOrEnvironmentShouldRequireManualReview() {
        assertThat(guard.inspect("\\begin{equation}\\label{eq:a}", "\\begin{align}\\label{eq:b}"))
                .isNotEmpty();
    }

    @Test
    void escapedDollarShouldNotBeCountedAsMathDelimiter() {
        assertThat(guard.inspect("费用为 \\$5。", "费用为 \\$5 美元。"))
                .isEmpty();
    }
}
