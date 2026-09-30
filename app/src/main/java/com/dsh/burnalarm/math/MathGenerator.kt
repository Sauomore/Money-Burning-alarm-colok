package com.dsh.burnalarm.math

import com.dsh.burnalarm.data.MathDifficulty
import kotlin.random.Random

data class MathProblem(
    val text: String,
    val answer: Int
)

/**
 * 生成心算题。目标是「躺着半睡半醒时也能算，但必须真的清醒过来」。
 */
object MathGenerator {

    fun generate(difficulty: MathDifficulty, count: Int, seed: Long = System.nanoTime()): List<MathProblem> {
        val rnd = Random(seed)
        val out = ArrayList<MathProblem>(count)
        var guard = 0
        while (out.size < count && guard++ < count * 20) {
            val p = when (difficulty) {
                MathDifficulty.EASY -> easy(rnd)
                MathDifficulty.NORMAL -> normal(rnd)
                MathDifficulty.HARD -> hard(rnd)
            }
            // 避免出现重复题目（答案和算式都一样）
            if (out.none { it.text == p.text }) out.add(p)
        }
        return out
    }

    private fun easy(r: Random): MathProblem {
        return if (r.nextBoolean()) {
            val a = r.nextInt(10, 90)
            val b = r.nextInt(10, 90)
            MathProblem("$a + $b", a + b)
        } else {
            val a = r.nextInt(30, 99)
            val b = r.nextInt(10, a)
            MathProblem("$a − $b", a - b)
        }
    }

    private fun normal(r: Random): MathProblem {
        return when (r.nextInt(3)) {
            0 -> {
                val a = r.nextInt(12, 99)
                val b = r.nextInt(3, 9)
                MathProblem("$a × $b", a * b)
            }
            1 -> {
                val a = r.nextInt(120, 899)
                val b = r.nextInt(100, 899)
                if (a >= b) MathProblem("$a − $b", a - b) else MathProblem("$b − $a", b - a)
            }
            else -> {
                val a = r.nextInt(20, 99)
                val b = r.nextInt(110, 899)
                MathProblem("$a + $b", a + b)
            }
        }
    }

    private fun hard(r: Random): MathProblem {
        return when (r.nextInt(4)) {
            0 -> {
                val a = r.nextInt(12, 49)
                val b = r.nextInt(12, 49)
                MathProblem("$a × $b", a * b)
            }
            1 -> {
                val a = r.nextInt(6, 19)
                val b = r.nextInt(6, 19)
                val c = r.nextInt(11, 40)
                MathProblem("$a × $b − $c", a * b - c)
            }
            2 -> {
                val a = r.nextInt(3, 12)
                val b = r.nextInt(11, 40)
                val c = r.nextInt(3, 9)
                MathProblem("($b + $c) × $a", (b + c) * a)
            }
            else -> {
                val a = r.nextInt(12, 99)
                val b = r.nextInt(3, 9)
                val c = r.nextInt(20, 200)
                MathProblem("$a × $b + $c", a * b + c)
            }
        }
    }
}
