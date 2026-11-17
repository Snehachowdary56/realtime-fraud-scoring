package com.sneha.fraud.rules;

import static com.sneha.fraud.rules.Txns.NOW;
import static com.sneha.fraud.rules.Txns.history;
import static com.sneha.fraud.rules.Txns.normal;
import static com.sneha.fraud.rules.Txns.txn;
import static com.sneha.fraud.rules.Txns.withNearLimit;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FraudRulesTest {

    @Nested
    class HighAmount {
        private final HighAmountRule rule = new HighAmountRule();

        @Test
        void flagsAmountFarAboveAverage() {
            var r = rule.evaluate(txn(900), normal());
            assertThat(r).isPresent();
            assertThat(r.get().points()).isEqualTo(40);
            assertThat(r.get().reason()).contains("11.3x");
        }

        @Test
        void ignoresNormalAmount() {
            assertThat(rule.evaluate(txn(120), normal())).isEmpty();
        }

        @Test
        void ignoresSmallAmountEvenIfManyTimesAverage() {
            var tinyAverage = history(0, 40, 5.0, true, "CA", NOW.minusSeconds(3600));
            assertThat(rule.evaluate(txn(100), tinyAverage)).isEmpty();
        }

        @Test
        void newAccountUsesFixedLimit() {
            assertThat(rule.evaluate(txn(3500), AccountHistory.empty())).isPresent();
            assertThat(rule.evaluate(txn(2500), AccountHistory.empty())).isEmpty();
        }
    }

    @Nested
    class Velocity {
        private final VelocityRule rule = new VelocityRule();

        @Test
        void flagsFifthTransactionInTenMinutes() {
            assertThat(rule.evaluate(txn(20), history(4, 40, 80, true, "CA", NOW))).isPresent();
        }

        @Test
        void ignoresNormalPace() {
            assertThat(rule.evaluate(txn(20), history(3, 40, 80, true, "CA", NOW))).isEmpty();
        }
    }

    @Nested
    class NewDevice {
        private final NewDeviceRule rule = new NewDeviceRule();

        @Test
        void flagsUnknownDeviceOnExistingAccount() {
            assertThat(rule.evaluate(txn(50), history(0, 40, 80, false, "CA", NOW))).isPresent();
        }

        @Test
        void ignoresKnownDevice() {
            assertThat(rule.evaluate(txn(50), normal())).isEmpty();
        }

        @Test
        void ignoresBrandNewAccount() {
            assertThat(rule.evaluate(txn(50), AccountHistory.empty())).isEmpty();
        }
    }

    @Nested
    class ImpossibleTravel {
        private final ImpossibleTravelRule rule = new ImpossibleTravelRule();

        @Test
        void flagsCountryChangeWithinAnHour() {
            var h = history(0, 40, 80, true, "CA", NOW.minusSeconds(20 * 60));
            var r = rule.evaluate(txn(50, "electronics", "BR", "dev-1"), h);
            assertThat(r).isPresent();
            assertThat(r.get().reason()).isEqualTo("CA to BR in 20 minutes");
        }

        @Test
        void allowsCountryChangeAfterLongGap() {
            var h = history(0, 40, 80, true, "CA", NOW.minusSeconds(10 * 3600));
            assertThat(rule.evaluate(txn(50, "travel", "FR", "dev-1"), h)).isEmpty();
        }

        @Test
        void ignoresSameCountryAndNoHistory() {
            assertThat(rule.evaluate(txn(50), normal())).isEmpty();
            assertThat(rule.evaluate(txn(50, "travel", "FR", "dev-1"), AccountHistory.empty())).isEmpty();
        }
    }

    @Nested
    class HighRiskMerchant {
        private final HighRiskMerchantRule rule = new HighRiskMerchantRule();

        @Test
        void flagsGiftCardsAndCrypto() {
            assertThat(rule.evaluate(txn(50, "gift_cards", "CA", "dev-1"), normal())).isPresent();
            assertThat(rule.evaluate(txn(50, "crypto", "CA", "dev-1"), normal())).isPresent();
        }

        @Test
        void ignoresEverydayCategories() {
            assertThat(rule.evaluate(txn(50, "groceries", "CA", "dev-1"), normal())).isEmpty();
        }
    }

    @Nested
    class AmlStructuring {
        private final StructuringRule rule = new StructuringRule();

        @Test
        void flagsThirdDepositJustUnderReportingLimit() {
            var r = rule.evaluate(txn(9600), withNearLimit(2));
            assertThat(r).isPresent();
            assertThat(r.get().points()).isEqualTo(50);
            assertThat(r.get().reason()).contains("3 transactions just under $10,000");
        }

        @Test
        void ignoresFirstTwoAndAmountsAtOrAboveLimit() {
            assertThat(rule.evaluate(txn(9600), withNearLimit(1))).isEmpty();
            assertThat(rule.evaluate(txn(10000), withNearLimit(5))).isEmpty();
            assertThat(rule.evaluate(txn(8999.99), withNearLimit(5))).isEmpty();
        }
    }
}
