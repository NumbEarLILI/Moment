package com.example.moment.data.nas

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归：fragmentImageIndices 仅含部分下标时，剩余槽位须由第二阶段轮询写入各碎片。
 */
class NasDiaryImageRestoreMergeTest {

    @Test
    fun partialIndices_thenRoundRobin_fillsAllSlots() {
        val resolved = arrayOfNulls<String>(3)
        resolved[0] = "a"
        resolved[1] = "b"
        resolved[2] = "c"
        val fragmentImageIndices = mapOf("s0" to listOf(0))
        val stableIds = listOf("s0", "s1", "s2")

        val buckets = mergeRestoredFragmentBuckets(stableIds, fragmentImageIndices, resolved)

        assertEquals(listOf("a"), buckets["s0"])
        assertEquals(listOf("b"), buckets["s1"])
        assertEquals(listOf("c"), buckets["s2"])
    }

    @Test
    fun trimFragmentKeys_matchStableIds() {
        val resolved = arrayOfNulls<String>(2)
        resolved[0] = "u0"
        resolved[1] = "u1"
        val fragmentImageIndices = mapOf("  s0 \n" to listOf(0))
        val stableIds = listOf("s0", "s1")

        val buckets = mergeRestoredFragmentBuckets(stableIds, fragmentImageIndices, resolved)

        assertEquals(listOf("u0"), buckets["s0"])
        assertEquals(listOf("u1"), buckets["s1"])
    }

    @Test
    fun emptyIndices_allRoundRobin() {
        val resolved = arrayOfNulls<String>(3)
        resolved[0] = "x"
        resolved[1] = "y"
        resolved[2] = "z"
        val stableIds = listOf("a", "b")

        val buckets = mergeRestoredFragmentBuckets(stableIds, emptyMap(), resolved)

        assertEquals(listOf("x", "z"), buckets["a"])
        assertEquals(listOf("y"), buckets["b"])
    }
}
