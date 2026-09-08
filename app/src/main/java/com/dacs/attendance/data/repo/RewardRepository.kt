package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.RewardConfigRow
import com.dacs.attendance.data.remote.RewardDayRow
import com.dacs.attendance.domain.RewardDay
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The `attendance_reward_progress` RPC from migration 0066. */
private const val REWARD_PROGRESS = "attendance_reward_progress"

interface RewardRepository {

    /**
     * The five Monday–Friday rows for the week beginning [weekStart].
     *
     * Live, never frozen: this answers for the week the worker is
     * standing in. The reward that actually gets paid is written
     * server-side by `attendance_evaluate_week` once the week has ended
     * and its grace period has passed.
     */
    suspend fun weekProgress(weekStart: LocalDate): Result<List<RewardDay>>

    /**
     * What a qualifying week is worth, in pesos.
     *
     * Read from `attendance_config` rather than held as a constant. ₱500
     * is the documented MVP default and it is configurable per owner, so
     * a literal in the app would be a second copy of a value the
     * database owns -- wrong the first time anybody changes it, and
     * wrong silently.
     *
     * Null when the owner has no config row yet. The screen then shows
     * the standing without a figure, which is honest: it can say the
     * week is on track without naming a number nobody has set.
     */
    suspend fun rewardAmount(): Result<Double?>
}

/**
 * ── THE ONE PART OF THIS APP WITH NO OFFLINE MIRROR, on purpose.
 *
 *    Everything else here is built to work on a site with no bars: the
 *    submission queue, today's record, the project list and History all
 *    have Room mirrors precisely because a worker needs them where there
 *    is no signal.
 *
 *    The reward strip deliberately does not. Qualification depends on
 *    facts this device does not hold -- which days the site was closed,
 *    what cutoff its project carries, what the server has since received
 *    from other days captured offline. A locally computed answer would
 *    sometimes disagree with the frozen record, and a worker told they
 *    had earned ₱500 who then does not receive it is a far worse outcome
 *    than a worker who has to wait for signal to check.
 *
 *    So a failure here means the strip is simply absent, and the screen
 *    says why. It never guesses.
 */
@Singleton
class SupabaseRewardRepository @Inject constructor(
    private val client: SupabaseClient
) : RewardRepository {

    override suspend fun weekProgress(weekStart: LocalDate): Result<List<RewardDay>> =
        runCatchingExceptCancellation {
            val workerId = client.auth.currentUserOrNull()?.id
                ?: error("AUTH_REQUIRED")

            client.postgrest.rpc(
                REWARD_PROGRESS,
                buildJsonObject {
                    // Always this worker. The RPC refuses a mismatch
                    // unless the caller is an admin, so passing anyone
                    // else's id would simply be refused -- but the app
                    // should never be the thing that tries.
                    put("p_worker", workerId)
                    put("p_week_start", weekStart.toString())
                }
            ).decodeList<RewardDayRow>().mapNotNull { it.toDomain() }
        }

    override suspend fun rewardAmount(): Result<Double?> =
        runCatchingExceptCancellation {
            client.postgrest.from("attendance_config")
                .select(Columns.list("reward_amount"))
                .decodeList<RewardConfigRow>()
                .firstOrNull()
                ?.rewardAmount
        }
}
