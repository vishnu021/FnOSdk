package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

/**
 * Shared state and behaviour for every {@link ActiveOrder} implementation: entry/exit book-keeping,
 * stop-loss revision history, realised-profit accrual, and the free-form {@code extraData} bag that
 * is serialised into the orderLog.
 *
 * <h2>Excursion instrumentation — {@code maxFavourableExcursion} / {@code maxAdverseExcursion}</h2>
 *
 * <p><b>Hypothesis.</b> Every excursion number we can currently quote is censored by the measuring
 * arm's own exit, so exit-design questions have to be answered by running another arm instead of by
 * querying a log. The 2026-07-23 3Sold stop-loss A/B is the worked example: BREAKEVEN_TRAILING_5's
 * favourable travel is only recoverable up to its <i>first</i> 50% retracement, and STEPPED's
 * "reached 66% of target" figure is depressed because 15 winners had already been killed at
 * breakeven — so every loser-reach percentage in that evidence set is a lower bound, not a
 * measurement. Recording the running high-water and low-water mark on the ORDER, where it is
 * untouched by whichever stop-loss happens to be selected, collapses that band to a point estimate
 * and makes the whole class of question a query rather than a run.
 *
 * <p><b>Exact rule.</b> On every tick of the symbol that drives this order's sell loop, compute
 * {@code move = isCallOrder() ? ltp - buyPrice : buyPrice - ltp} — the move signed in the trade's
 * own direction, so positive is always favourable regardless of CE/PE. Keep the running maximum in
 * {@code maxFavourableExcursion} and the running minimum in {@code maxAdverseExcursion}. That is the
 * entire rule. This class does not read either value, does not compare them to anything, does not
 * call {@link ActiveOrder#setStopLoss(double)}, and does not influence any exit decision.
 *
 * <p><b>Why this cannot truncate the right tail.</b> Because it never acts. The measured
 * dose-response on 3Sold (152 paired trades, identical entries, per lot) is monotone in how early
 * and how aggressively the stop ratchets: BREAKEVEN_TRAILING_5, which arms from entry, cut AvgWin
 * by 90% and turned +Rs 50,824 into -Rs 6,118; STEPPED, arming at 33%/66% of target, cut AvgWin 41%;
 * PARTIAL_REVISING, which arms only after a partial sell has banked cash, was the single arm to
 * <i>raise</i> AvgWin (+1%). The damage in every case came from moving the stop. This code moves
 * nothing: it is a pure recorder on the same price series the exit decision already consumes, so
 * the recorded FIXED arm must remain P&amp;L-identical to its prior revision. Any deviation is a bug,
 * not a result — see the kill criterion below.
 *
 * <p><b>No thresholds.</b> This instrumentation deliberately declares no constants: no buffer, no
 * arming level, no R-multiple, no index-point literal. There is nothing to calibrate and therefore
 * nothing that can be miscalibrated across indices — which is exactly the defect that makes a shared
 * 5-index-point breakeven buffer roughly 3x the charge floor on NIFTY 50 and roughly 1x on SENSEX.
 * Conversion to R-multiples is a post-hoc analysis step
 * ({@code mfe / Math.abs(buyThreshold - orderRequest.stopLoss)}), performed off the hot path.
 *
 * <p><b>Lots requirement: 1 lot.</b> Entirely lot-independent — it records price excursion and
 * changes nothing about quantity, exits or timing, so it behaves identically at every lot count and
 * on every strategy in the fleet, not just 3Sold. It runs on the existing 1-lot configuration,
 * which is the fleet's actual shape. Contrast PARTIAL_REVISING, which silently degrades to FIXED
 * below 3 lots.
 *
 * <p><b>State lifetime.</b> Two primitive fields on the order. Lifetime equals order lifetime
 * exactly — no key, no map, no eviction, no cleanup callback, nothing to forget on the two exits
 * that bypass the stop-loss strategy entirely (day-end square-off and max-hold). The state is
 * collected with the order and cannot collide across simulated days, across indices, or across
 * successive orders sharing a tag. This is ADR-0058's conclusion for strategy objects — replacing
 * the object is the only reset that is correct by construction rather than by per-class discipline —
 * applied to exit state, and it is the standing answer to the stop-loss state-leak class.
 * Note in particular that this is <i>not</i> routed through {@code extraData}: that map is still a
 * plain {@link HashMap} (ADR-0006 accepted, not implemented), it is mutated by the broker
 * order-update callback thread, and {@link #getExtraData()} hands out a live view rather than a
 * snapshot — a per-tick write there would be a genuine data race plus string boxing on the hot path.
 *
 * <p><b>Thread safety.</b> Plain non-volatile doubles are correct here. The write happens only on
 * the sell path, which is the single broker WebSocket reading thread in production (the sell check
 * runs before the strategy-queue hand-off) and the single synchronous loop thread in backtest. The
 * only cross-thread reader is the orderLog serialiser at close, which happens-after via the same
 * thread that closed the order.
 *
 * <p><b>Cost.</b> Two comparisons and at most two field writes per tick per open order. No
 * allocation, no boxing, no string work, no branching on strings.
 *
 * <p><b>Kill criterion.</b> Kill immediately if the instrumented FIXED re-run is not P&amp;L-identical
 * to the recorded 01-fixed arm: 152/152 matched on {@code extraData.entryDateTime}, every
 * {@code realisedProfit} equal to the rupee, 66 winners, 85 STOP_LOSS_HIT, 66 TARGET_HIT,
 * 1 EXPIRY_TIME_REACHED, gross Rs 63,136.2. The only permitted diff is the two new JSON fields.
 * There is no "interesting result" branch — any P&amp;L deviation means the update call has a side
 * effect. Sign-convention gate: {@code maxFavourableExcursion} must be strictly positive for all 66
 * winners and {@code maxAdverseExcursion} must be non-positive for all 152 orders; an inverted
 * {@code isCallOrder()} branch would otherwise be silent, since 107 of the 152 trades are PE.
 * Geometry gate: every FIXED winner is a TARGET_HIT at 2.5R, so for all 66 winners
 * {@code (mfe + Math.abs(buyPrice - buyThreshold)) / Math.abs(buyThreshold - orderRequest.stopLoss)}
 * must be at least 2.5 — the slippage term is required because the excursion is anchored on the
 * actual fill while the target is derived from {@code buyThreshold}. A winner below that bound means
 * the excursion is not being updated on every tick, most likely because the call sits after an
 * early return.
 */
@Slf4j
@Getter
public abstract class AbstractActiveOrder implements ActiveOrder {
    protected final OrderRequest orderRequest;
    protected final int entryTimeStamp;
    protected int exitTimeStamp;
    protected double buyPrice;
    protected int buyQuantity;
    protected int soldQuantity;
    protected double sellPrice;
    protected double stopLoss;
    protected final Map<String, String> extraData;
    protected int stopLossRevisionCount;
    protected final Map<Integer, Double> stopLossRevision = new ConcurrentHashMap<>();
    protected double realisedProfit;

    /**
     * Broker-confirmed option buy price from Kite order-update callback.
     * Zero until the BUY leg completes with status=COMPLETE on a live broker.
     * Daily-analysis reports prefer this over {@code optionBuyPrice} / {@code buyPrice}
     * when computing real P&L because it removes the signal-time-vs-fill-time slippage bias.
     */
    protected double actualOptionBuyPrice;

    /** Broker-confirmed option sell price from Kite order-update callback. Zero until fill arrives. */
    protected double actualOptionSellPrice;

    /**
     * Peak favourable excursion, signed in the trade's own direction so positive is always
     * favourable for both CE and PE. Units follow {@code buyPrice}: INDEX POINTS for
     * {@link ActiveIndexOrder} / {@link TickBasedActiveOrder} / the multi-target variants (the sell
     * loop is driven by the index tick, because active orders are keyed by
     * {@code orderRequest.getIndex()}), and option premium for {@link OptionBasedActiveOrder}.
     *
     * <p>Zero until the first tick, and never negative: an order whose first observed tick is
     * adverse keeps 0.0 here. Uncensored by the stop-loss — see the excursion section on the class
     * Javadoc. Post-hoc R conversion is
     * {@code mfe / Math.abs(buyThreshold - orderRequest.stopLoss)}; the target VALUE is not usable
     * for this because {@code Target} serialises only {@code isMultiTarget}.
     */
    protected double maxFavourableExcursion;

    /**
     * Worst adverse excursion, signed in the same direction as {@link #maxFavourableExcursion} so
     * adverse readings are negative. Same units as that field.
     *
     * <p>Zero until the first tick, and never positive. Together with the favourable peak this gives
     * the true uncensored loser excursion distribution — the last piece of evidence that could
     * rehabilitate any pre-target arming idea, since the current strongest argument against
     * ratcheting (losers' median recovered excursion 6.5pt versus winners' 6.1pt) is itself censored
     * and could in principle be an artefact of the measuring arm.
     */
    protected double maxAdverseExcursion;

    protected AbstractActiveOrder(OrderRequest orderRequest,
                               double buyPrice,
                               int entryTimeStamp,
                               int buyQuantity,
                               String entryTimestamp) {
        this.orderRequest = orderRequest;
        this.entryTimeStamp = entryTimeStamp;
        this.buyPrice = buyPrice;
        this.buyQuantity = buyQuantity;
        this.soldQuantity = 0;
        this.stopLoss = orderRequest.getStopLoss();
        this.extraData = new HashMap<>(orderRequest.getExtraData());
        this.stopLossRevisionCount = 0;
        this.realisedProfit = 0;
        this.extraData.put("entryDateTime", entryTimestamp);
    }

    protected void updateStopLoss(double stopLoss) {
        stopLossRevision.put(++stopLossRevisionCount, this.stopLoss);
        this.stopLoss = stopLoss;
    }

    /**
     * Updates the running favourable/adverse excursion from the current tick. Implements
     * {@code ActiveOrder.updateExcursion(double)}; all five concrete order types inherit it, so
     * there is no per-type edit and no risk of one type being missed.
     *
     * <p>Called once per tick per open order from the sell path, as the first statement of the
     * active-order loop and therefore BEFORE any exit evaluation — including before the day-end
     * square-off and max-hold checks that return without ever consulting the stop-loss strategy.
     * That placement is load-bearing twice over: it is what makes the recording uncensored (it sees
     * every tick every stop-loss sees, on the same price series, whichever stop-loss is selected),
     * and it is what makes the geometry gate in the class Javadoc meaningful — a winner reading
     * below 2.5R means this call has drifted behind an early return.
     *
     * <p>Deliberately does not log: this is the hottest path in production, one call per tick per
     * open order on the WebSocket reading thread.
     *
     * @param ltp last traded price of the symbol driving this order's sell loop — the INDEX price
     *            for index and tick orders, the option premium for option-based orders, matching
     *            the units of {@code buyPrice} in both cases
     */
    public void updateExcursion(double ltp) {
        final double move = isCallOrder() ? ltp - buyPrice : buyPrice - ltp;
        if (move > maxFavourableExcursion) {
            maxFavourableExcursion = move;
        }
        if (move < maxAdverseExcursion) {
            maxAdverseExcursion = move;
        }
    }

    @Override
    public void setActualOptionBuyPrice(double actualOptionBuyPrice) {
        this.actualOptionBuyPrice = actualOptionBuyPrice;
    }

    @Override
    public void setActualOptionSellPrice(double actualOptionSellPrice) {
        this.actualOptionSellPrice = actualOptionSellPrice;
    }

    @Override
    public Map<String, String> getExtraData() {
        return Collections.unmodifiableMap(extraData);
    }

    @Override
    public void appendExtraData(String key, String value) {
        extraData.put(key, value);
    }

    @Override
    public void closeOrder(double closePrice, int timeIndex, String timestamp) {
        this.exitTimeStamp = timeIndex;
        this.sellPrice = closePrice;
        this.extraData.put("exitDateTime", timestamp);
        this.extraData.put("profit", String.valueOf(roundTo5Paise(getProfit())));
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        this.soldQuantity += soldQuantity;
        this.realisedProfit += soldQuantity * sellOptionPrice;
        log.info("Updated realised profit to: {} for order: {}", this.realisedProfit, this);
    }

    /**
     * Initializes realised profit based on buy price and quantity.
     * Called when the actual option buy price is set after order creation.
     *
     * @param buyOptionPrice the price at which the option was bought
     */
    protected void initializeRealisedProfit(double buyOptionPrice) {
        this.realisedProfit = -1 * (this.buyQuantity * buyOptionPrice);
        log.info("Initialized realised profit with: {}, buyOptionPrice: {} for order: {}",
                this.realisedProfit, buyOptionPrice, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(INDENTED_TAB)
                .append(getClass().getSimpleName()).append("{")
                .append("index=").append(orderRequest.getIndex())
                .append(", tag=").append(orderRequest.getTag());
        appendToStringFields(sb);
        sb.append(", buyPrice=").append(buyPrice)
                .append(", target=").append(orderRequest.getTarget())
                .append(", stopLoss=").append(roundTo5Paise(stopLoss))
                .append(", buyQ=").append(buyQuantity)
                .append(", soldQ=").append(soldQuantity);
        if(this.extraData.containsKey("kiteOrderId")) {
            sb.append(", kiteOrderId=").append(extraData.get("kiteOrderId"));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void appendToStringFields(StringBuilder sb) {
        // Override in subclasses to add extra fields
    }
}
