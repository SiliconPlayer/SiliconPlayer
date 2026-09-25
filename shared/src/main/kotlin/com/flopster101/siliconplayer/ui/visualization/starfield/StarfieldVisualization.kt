package com.flopster101.siliconplayer.ui.visualization.starfield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

private class Star(
    var x: Float,
    var y: Float,
    var z: Float,
    var age: Float
)

private class StarfieldSimState(
    initialCount: Int,
    near: Float
) {
    val stars = ArrayList<Star>(initialCount)
    var lastNanos = 0L
    var elapsed = 0f
    var energySmooth = 0f
    var fast = 0f
    var slow = 0f
    var driveSmooth = 0f

    init {
        ensureStars(initialCount, near)
    }

    fun ensureStars(count: Int, near: Float) {
        val safeNear = near.coerceIn(0.005f, 0.5f)
        while (stars.size < count) {
            val z = safeNear + Random.nextFloat() * (1f - safeNear)
            val age = Random.nextFloat() * 2f
            stars.add(Star(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f, z, age))
        }
        while (stars.size > count) {
            stars.removeAt(stars.lastIndex)
        }
    }

    fun respawn(star: Star, atFar: Boolean, near: Float) {
        val safeNear = near.coerceIn(0.005f, 0.5f)
        star.age = 0f
        star.x = Random.nextFloat() * 2f - 1f
        star.y = Random.nextFloat() * 2f - 1f
        star.z = if (atFar) {
            0.92f + Random.nextFloat() * 0.08f
        } else {
            safeNear + Random.nextFloat() * (1f - safeNear)
        }
    }
}

@Composable
fun StarfieldVisualization(
    isPlaying: Boolean,
    vuLevels: FloatArray,
    bars: FloatArray,
    starCount: Int,
    speed: Float,
    fov: Float,
    nearPlane: Float,
    starColorArgb: Int,
    baseSizePx: Float,
    sizeGrowth: Float,
    farDim: Float,
    softness: Float,
    beatGlow: Float,
    glowSize: Float,
    trailPersistence: Float,
    streaks: Boolean,
    streakLength: Float,
    centerX: Float,
    centerY: Float,
    autoDrift: Boolean,
    beatFollow: Boolean,
    reactSpeed: Float,
    flash: Float,
    square: Boolean,
    modifier: Modifier = Modifier
) {
    val safeCount = starCount.coerceIn(1, 2048)
    val safeNear = nearPlane.coerceIn(0.005f, 0.5f)
    val simState = remember { StarfieldSimState(safeCount, safeNear) }
    var frameTick by remember { mutableLongStateOf(0L) }

    val baseColor = remember(starColorArgb) { Color(starColorArgb) }

    LaunchedEffect(isPlaying) {
        simState.lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val dt = if (simState.lastNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - simState.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                simState.lastNanos = nowNanos
                simState.ensureStars(safeCount, safeNear)

                val effectiveDt = if (isPlaying) dt else 0f
                simState.elapsed += effectiveDt

                val vuRaw = if (beatFollow && isPlaying) {
                    max(vuLevels.getOrElse(0) { 0f }, vuLevels.getOrElse(1) { 0f }).coerceIn(0f, 1f)
                } else 0f

                var bass = 0f
                if (beatFollow && isPlaying && bars.isNotEmpty()) {
                    val bassBins = min(bars.size, 48)
                    for (i in 0 until bassBins) {
                        if (bars[i] > bass) bass = bars[i]
                    }
                }
                val bassRaw = bass.coerceIn(0f, 1f)

                val target = min(1f, max(vuRaw, bassRaw * 1.25f))
                if (target > simState.energySmooth) {
                    simState.energySmooth = target
                } else if (effectiveDt > 0f) {
                    simState.energySmooth += (target - simState.energySmooth) * min(1f, effectiveDt * 5f)
                }

                if (effectiveDt > 0f) {
                    simState.fast += (target - simState.fast) * min(1f, effectiveDt * 25f)
                    if (target - simState.slow > 0.6f) {
                        simState.slow = target
                        simState.fast = target
                    } else {
                        val slowRate = if (target < simState.slow) effectiveDt * 0.5f else effectiveDt * 0.4f
                        simState.slow += (target - simState.slow) * min(1f, slowRate)
                    }
                } else {
                    simState.fast = target
                    simState.slow = target
                }

                val energy = if (beatFollow) simState.energySmooth.pow(0.75f) else 0f
                val novelty = if (beatFollow) {
                    (((simState.fast - simState.slow) / max(simState.slow, 0.15f)) * 5f).coerceIn(0f, 1f)
                } else 0f

                val drive = energy * 0.85f + novelty * 0.6f
                if (drive > simState.driveSmooth) {
                    simState.driveSmooth = drive
                } else if (effectiveDt > 0f) {
                    simState.driveSmooth += (drive - simState.driveSmooth) * min(1f, effectiveDt * 8f)
                }

                val effectiveDrive = min(1.15f, simState.driveSmooth)
                val reactTerm = effectiveDrive.pow(1.2f)
                val spd = speed * (1f + reactSpeed * reactTerm)

                for (i in 0 until simState.stars.size) {
                    val star = simState.stars[i]
                    if (effectiveDt > 0f) {
                        star.age += effectiveDt
                        star.z -= spd * effectiveDt
                        if (star.z <= safeNear) {
                            simState.respawn(star, atFar = true, safeNear)
                        } else {
                            val s = fov / star.z
                            if (abs(star.x * s) > 1.15f || abs(star.y * s) > 1.15f) {
                                simState.respawn(star, atFar = false, safeNear)
                            }
                        }
                    }
                }
                frameTick = nowNanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick

        val halfW = size.width * 0.5f
        val halfH = size.height * 0.5f
        if (halfW <= 0f || halfH <= 0f) return@Canvas

        val energy = if (beatFollow) simState.energySmooth.pow(0.75f) else 0f
        val novelty = if (beatFollow) {
            (((simState.fast - simState.slow) / max(simState.slow, 0.15f)) * 5f).coerceIn(0f, 1f)
        } else 0f
        val flashBoost = 1f + flash * max(energy, novelty) * 1.5f

        val driftX = if (autoDrift) sin(simState.elapsed * 0.24f) * 0.12f else 0f
        val driftY = if (autoDrift) cos(simState.elapsed * 0.17f) * 0.10f else 0f
        val cx = centerX + driftX
        val cy = centerY + driftY

        val sizeK = min(size.height, 1080f) / 1080f
        val depthRange = max(0.001f, 1f - safeNear)

        val effectiveDrive = min(1.15f, simState.driveSmooth)
        val reactTerm = effectiveDrive.pow(1.2f)
        val spd = speed * (1f + reactSpeed * reactTerm)

        val starList = simState.stars
        for (i in 0 until starList.size) {
            val star = starList[i]
            val z = max(1e-3f, star.z)
            val s = fov / z
            val x = halfW + (cx + star.x * s) * halfW
            val y = halfH + (cy + star.y * s) * halfH

            val depth = 1f - (z - safeNear) / depthRange
            val sz = max(1f, baseSizePx * sizeK * (1f + sizeGrowth * depth * depth))
            val al = (1f - farDim * (1f - depth)).coerceIn(0f, 1f) * flashBoost
            val fadeIn = min(1f, star.age / 0.35f)
            val fadeOut = ((z - safeNear) / (depthRange * 0.10f)).coerceIn(0f, 1f)
            val env = fadeIn * fadeOut
            val finalAlpha = (min(1f, al) * env).coerceIn(0f, 1f)

            if (streaks && env > 0.004f) {
                val zp = z + spd * 0.016f * streakLength * 8f
                val sp = fov / zp
                val tx = halfW + (cx + star.x * sp) * halfW
                val ty = halfH + (cy + star.y * sp) * halfH
                val streakAlpha = (0.55f * min(flashBoost, 1.5f) * env).coerceIn(0f, 1f)
                drawLine(
                    color = baseColor.copy(alpha = streakAlpha),
                    start = Offset(x + (tx - x) * env, y + (ty - y) * env),
                    end = Offset(x, y),
                    strokeWidth = max(1f, sz * 0.4f)
                )
            } else if (!streaks && finalAlpha > 0f) {
                if (square) {
                    drawRect(
                        color = baseColor.copy(alpha = finalAlpha),
                        topLeft = Offset(x - sz * 0.5f, y - sz * 0.5f),
                        size = Size(sz, sz)
                    )
                } else {
                    drawCircle(
                        color = baseColor.copy(alpha = finalAlpha),
                        radius = sz * 0.5f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
