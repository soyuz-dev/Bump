package org.soyuz.util.math
import org.soyuz.util.Debug
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3D(val x: Double, val y: Double, val z: Double) {

    companion object {
        // Common constants
        val ZERO = Vector3D(0.0, 0.0, 0.0)
        val UNIT_X = Vector3D(1.0, 0.0, 0.0)
        val UNIT_Y = Vector3D(0.0, 1.0, 0.0)
        val UNIT_Z = Vector3D(0.0, 0.0, 1.0)

        const val EPSILON_ZERO = 1e-8      // for "is this basically zero?"
        const val EPSILON_COMPARE = 1e-6   // for equality checks
        const val EPSILON_NORMALIZE = 1e-12 // for avoiding divide-by-zero

    }

    // Basic arithmetic
    operator fun plus(other: Vector3D): Vector3D =
        Vector3D(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3D): Vector3D =
        Vector3D(x - other.x, y - other.y, z - other.z)

    operator fun unaryMinus(): Vector3D =
        Vector3D(-x, -y, -z)

    operator fun times(scalar: Double): Vector3D =
        Vector3D(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Double): Vector3D =
        if (abs(scalar) > EPSILON_NORMALIZE) {
            Vector3D(x / scalar, y / scalar, z / scalar)
        } else {
            Debug.log{ "Division by zero detected. Did you intend scalar to be zero?" }
            ZERO
        }

    // Length & normalization
    fun length(): Double =
        sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Double =
        x * x + y * y + z * z

    fun normalized(): Vector3D {
        val len = length()
        if (len < EPSILON_NORMALIZE) return ZERO
        val invLen = 1.0 / len
        return Vector3D(x * invLen, y * invLen, z * invLen)
    }

    // Dot & cross products
    infix fun dot(other: Vector3D): Double =
        x * other.x + y * other.y + z * other.z

    /**
     * The 3D cross product.
     */
    infix fun cross(other: Vector3D): Vector3D {
        return Vector3D(
            x = (y * other.z) - (z * other.y),
            y = (z * other.x) - (x * other.z),
            z = (x * other.y) - (y * other.x)
        )
    }

    /**
     * Returns a vector perpendicular to this one (rotated 90° CCW).
     */

    // Distance
    fun distance(to: Vector3D): Double =
        (this - to).length()

    // Projection and reflection
    fun project(onto: Vector3D): Vector3D {
        val norm = onto.normalized()
        return norm * (dot(norm))
    }

    fun reflect(normal: Vector3D): Vector3D {
        // v' = v - 2*(v·n)*n
        val d = dot(normal)
        return this - (normal * (2.0 * d))
    }

    fun lerp(to: Vector3D, t: Double): Vector3D =
        this + (to - this) * t

    fun clamp(maxLength: Double): Vector3D {
        val lenSq = lengthSquared()
        if (lenSq > maxLength * maxLength) {
            return normalized() * maxLength
        }
        return this
    }

    fun isZero(): Boolean =
        abs(x) < EPSILON_ZERO && abs(y) < EPSILON_ZERO && abs(z) < EPSILON_ZERO

    fun isCloseTo(other: Vector3D): Boolean {
        if (this === other) return true

        return abs(x - other.x) < EPSILON_COMPARE &&
                abs(y - other.y) < EPSILON_COMPARE &&
                abs(z - other.z) < EPSILON_COMPARE
    }


    override fun toString(): String =
        "Vector3D[$x, $y, $z]"
}

operator fun Double.times(v: Vector3D): Vector3D =
    Vector3D(this * v.x, this * v.y, this * v.z)