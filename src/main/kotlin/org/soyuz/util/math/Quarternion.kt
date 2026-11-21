package org.soyuz.util.math

import kotlin.math.*
data class Quaternion(
    val w: Double,
    val x: Double,
    val y: Double,
    val z: Double
) {
    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        fun fromAxisAngle(axis: Vector3D, radians: Double): Quaternion {
            val halfAngle = radians / 2.0
            val s = sin(halfAngle)
            val n = axis.normalized()
            return Quaternion(cos(halfAngle), n.x * s, n.y * s, n.z * s)
        }

        fun fromEuler(roll: Double, pitch: Double, yaw: Double): Quaternion {
            // Standard ZYX rotation order
            val cr = cos(roll / 2.0); val sr = sin(roll / 2.0)
            val cp = cos(pitch / 2.0); val sp = sin(pitch / 2.0)
            val cy = cos(yaw / 2.0); val sy = sin(yaw / 2.0)

            return Quaternion(
                w = cr * cp * cy + sr * sp * sy,
                x = sr * cp * cy - cr * sp * sy,
                y = cr * sp * cy + sr * cp * sy,
                z = cr * cp * sy - sr * sp * cy
            )
        }

        fun fromAxisAngle(vector: Vector3D): Quaternion {
            // Treats the Vector3D as an axis-angle rotation where the direction is the axis and the magnitude is the angle in radians
            val angle = vector.length()
            if (angle < Vector3D.EPSILON_NORMALIZE) {
                return IDENTITY
            }
            return fromAxisAngle(vector / angle, angle)
        }
        
    }

    operator fun times(other: Quaternion): Quaternion {
        return Quaternion(
            w = w * other.w - x * other.x - y * other.y - z * other.z,
            x = w * other.x + x * other.w + y * other.z - z * other.y,
            y = w * other.y - x * other.z + y * other.w + z * other.x,
            z = w * other.z + x * other.y - y * other.x + z * other.w
        )
    }

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    fun norm(): Double = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm()
        if (n < Vector3D.EPSILON_NORMALIZE) return IDENTITY
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    fun rotate(vec: Vector3D): Vector3D {
        val q = normalized()
        val v = Quaternion(0.0, vec.x, vec.y, vec.z)
        val result = q * v * q.conjugate()
        return Vector3D(result.x, result.y, result.z)
    }

    fun toRotationMatrix(): Matrix4f {
        val q = normalized()
        val xx = q.x * q.x; val yy = q.y * q.y; val zz = q.z * q.z
        val xy = q.x * q.y; val xz = q.x * q.z; val yz = q.y * q.z
        val wx = q.w * q.x; val wy = q.w * q.y; val wz = q.w * q.z

        return Matrix4f(floatArrayOf(
            (1f - 2f * (yy + zz)).toFloat(), (2f * (xy + wz)).toFloat(), (2f * (xz - wy)).toFloat(), 0f,
            (2f * (xy - wz)).toFloat(), (1f - 2f * (xx + zz)).toFloat(), (2f * (yz + wx)).toFloat(), 0f,
            (2f * (xz + wy)).toFloat(), (2f * (yz - wx)).toFloat(), (1f - 2f * (xx + yy)).toFloat(), 0f,
            0f, 0f, 0f, 1f
        ))
    }

    fun slerp(other: Quaternion, t: Double): Quaternion {
        val q1 = normalized()
        val q2 = other.normalized()

        var dot = q1.w * q2.w + q1.x * q2.x + q1.y * q2.y + q1.z * q2.z

        // Take shortest path
        var q2Adjusted = q2
        if (dot < 0.0) {
            q2Adjusted = Quaternion(-q2.w, -q2.x, -q2.y, -q2.z)
            dot = -dot
        }

        if (dot > 0.9995) {
            // Nearly parallel — linear interpolation is fine
            val result = Quaternion(
                q1.w + (q2Adjusted.w - q1.w) * t,
                q1.x + (q2Adjusted.x - q1.x) * t,
                q1.y + (q2Adjusted.y - q1.y) * t,
                q1.z + (q2Adjusted.z - q1.z) * t
            )
            return result.normalized()
        }

        val theta0 = acos(dot)
        val theta = theta0 * t
        val sinTheta = sin(theta)
        val sinTheta0 = sin(theta0)

        val s0 = cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0

        return Quaternion(
            s0 * q1.w + s1 * q2Adjusted.w,
            s0 * q1.x + s1 * q2Adjusted.x,
            s0 * q1.y + s1 * q2Adjusted.y,
            s0 * q1.z + s1 * q2Adjusted.z
        )
    }
}