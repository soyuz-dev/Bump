package org.soyuz.util.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*

class Vector3DTest {

    @Test
    fun `construction and accessors`() {
        val v = Vector3D(1.0, 2.0, 3.0)
        assertEquals(1.0, v.x)
        assertEquals(2.0, v.y)
        assertEquals(3.0, v.z)
    }

    @Test
    fun `addition`() {
        val a = Vector3D(1.0, 2.0, 3.0)
        val b = Vector3D(4.0, 5.0, 6.0)
        assertEquals(Vector3D(5.0, 7.0, 9.0), a + b)
    }

    @Test
    fun `subtraction`() {
        val a = Vector3D(5.0, 7.0, 9.0)
        val b = Vector3D(1.0, 2.0, 3.0)
        assertEquals(Vector3D(4.0, 5.0, 6.0), a - b)
    }

    @Test
    fun `unary minus`() {
        val v = Vector3D(1.0, -2.0, 3.0)
        assertEquals(Vector3D(-1.0, 2.0, -3.0), -v)
    }

    @Test
    fun `scalar multiplication`() {
        val v = Vector3D(1.0, 2.0, 3.0)
        assertEquals(Vector3D(2.0, 4.0, 6.0), v * 2.0)
        assertEquals(Vector3D(2.0, 4.0, 6.0), 2.0 * v)
    }

    @Test
    fun `scalar division`() {
        val v = Vector3D(10.0, 20.0, 30.0)
        assertEquals(Vector3D(5.0, 10.0, 15.0), v / 2.0)
    }

    @Test
    fun `division by zero returns zero`() {
        val v = Vector3D(1.0, 2.0, 3.0)
        assertEquals(Vector3D.ZERO, v / 0.0)
    }

    @Test
    fun `length`() {
        assertEquals(5.0, Vector3D(3.0, 4.0, 0.0).length(), 1e-9)
        assertEquals(sqrt(14.0), Vector3D(1.0, 2.0, 3.0).length(), 1e-9)
    }

    @Test
    fun `lengthSquared`() {
        assertEquals(14.0, Vector3D(1.0, 2.0, 3.0).lengthSquared(), 1e-9)
    }

    @Test
    fun `normalized produces unit vector`() {
        val v = Vector3D(3.0, 4.0, 0.0)
        val n = v.normalized()
        assertEquals(1.0, n.length(), 1e-9)
        assertEquals(0.6, n.x, 1e-9)
        assertEquals(0.8, n.y, 1e-9)
    }

    @Test
    fun `normalized zero vector returns zero`() {
        assertEquals(Vector3D.ZERO, Vector3D.ZERO.normalized())
    }

    @Test
    fun `dot product`() {
        val a = Vector3D(1.0, 2.0, 3.0)
        val b = Vector3D(4.0, 5.0, 6.0)
        assertEquals(32.0, a dot b, 1e-9)
    }

    @Test
    fun `cross product`() {
        val a = Vector3D(1.0, 0.0, 0.0)
        val b = Vector3D(0.0, 1.0, 0.0)
        assertEquals(Vector3D(0.0, 0.0, 1.0), a cross b)
    }

    @Test
    fun `cross product is anti-commutative`() {
        val a = Vector3D(1.0, 2.0, 3.0)
        val b = Vector3D(4.0, 5.0, 6.0)
        assertEquals(a cross b, -(b cross a))
    }

    @Test
    fun `cross product of parallel vectors is zero`() {
        val a = Vector3D(1.0, 2.0, 3.0)
        val b = Vector3D(2.0, 4.0, 6.0)
        assertTrue((a cross b).isZero())
    }

    @Test
    fun `distance`() {
        assertEquals(5.0, Vector3D(0.0, 0.0, 0.0).distance(Vector3D(3.0, 4.0, 0.0)), 1e-9)
    }

    @Test
    fun `projection onto axis`() {
        val v = Vector3D(3.0, 4.0, 0.0)
        val proj = v.project(Vector3D.UNIT_X)
        assertEquals(Vector3D(3.0, 0.0, 0.0), proj)
    }

    @Test
    fun `reflection`() {
        val v = Vector3D(1.0, -1.0, 0.0)
        val reflected = v.reflect(Vector3D.UNIT_Y)
        assertEquals(Vector3D(1.0, 1.0, 0.0), reflected)
    }

    @Test
    fun `lerp halfway`() {
        val a = Vector3D(0.0, 0.0, 0.0)
        val b = Vector3D(10.0, 20.0, 30.0)
        assertEquals(Vector3D(5.0, 10.0, 15.0), a.lerp(b, 0.5))
    }

    @Test
    fun `clamp limits length`() {
        val v = Vector3D(3.0, 4.0, 0.0)  // length 5
        val clamped = v.clamp(2.0)
        assertEquals(2.0, clamped.length(), 1e-9)
    }

    @Test
    fun `clamp leaves short vectors unchanged`() {
        val v = Vector3D(1.0, 1.0, 1.0)
        assertEquals(v, v.clamp(5.0))
    }

    @Test
    fun `isZero`() {
        assertTrue(Vector3D.ZERO.isZero())
        assertTrue(Vector3D(1e-9, 0.0, 0.0).isZero())
        assertFalse(Vector3D(0.1, 0.0, 0.0).isZero())
    }

    @Test
    fun `isCloseTo`() {
        val a = Vector3D(1.0, 2.0, 3.0)
        val b = Vector3D(1.0000001, 2.0000001, 3.0000001)
        assertTrue(a.isCloseTo(b))
        assertFalse(a.isCloseTo(Vector3D(10.0, 2.0, 3.0)))
    }
}

class QuaternionTest {

    @Test
    fun `identity quaternion`() {
        val q = Quaternion.IDENTITY
        assertEquals(1.0, q.w)
        assertEquals(0.0, q.x)
        assertEquals(0.0, q.y)
        assertEquals(0.0, q.z)
    }

    @Test
    fun `identity rotation leaves vector unchanged`() {
        val v = Vector3D(1.0, 2.0, 3.0)
        assertEquals(v, Quaternion.IDENTITY.rotate(v))
    }

    @Test
    fun `fromAxisAngle rotates 90 degrees around Z`() {
        val q = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = q.rotate(v)
        assertEquals(0.0, rotated.x, 1e-9)
        assertEquals(1.0, rotated.y, 1e-9)
        assertEquals(0.0, rotated.z, 1e-9)
    }

    @Test
    fun `fromAxisAngle vector form`() {
        val q = Quaternion.fromAxisAngle(Vector3D(0.0, 0.0, PI / 2.0))
        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = q.rotate(v)
        assertEquals(0.0, rotated.x, 1e-9)
        assertEquals(1.0, rotated.y, 1e-9)
    }

    @Test
    fun `rotation by 180 degrees flips vector`() {
        val q = Quaternion.fromAxisAngle(Vector3D.UNIT_Y, PI)
        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = q.rotate(v)
        assertEquals(-1.0, rotated.x, 1e-9)
        assertEquals(0.0, rotated.y, 1e-9)
    }

    @Test
    fun `quaternion multiplication composes rotations`() {
        val q1 = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val q2 = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val combined = q1 * q2  // 180 degrees total

        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = combined.rotate(v)
        assertEquals(-1.0, rotated.x, 1e-9)
        assertEquals(0.0, rotated.y, 1e-9)
    }

    @Test
    fun `normalized quaternion has unit norm`() {
        val q = Quaternion(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, q.normalized().norm(), 1e-9)
    }

    @Test
    fun `conjugate reverses rotation`() {
        val q = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val qInv = q.conjugate()
        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = qInv.rotate(q.rotate(v))
        assertEquals(v.x, rotated.x, 1e-9)
        assertEquals(v.y, rotated.y, 1e-9)
    }

    @Test
    fun `slerp halfway between identical quaternions`() {
        val q = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val result = q.slerp(q, 0.5)
        assertTrue(result.isCloseToQuaternion(q))
    }

    @Test
    fun `slerp halfway between identity and 90 degrees`() {
        val q1 = Quaternion.IDENTITY
        val q2 = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val halfway = q1.slerp(q2, 0.5)

        val v = Vector3D(1.0, 0.0, 0.0)
        val rotated = halfway.rotate(v)

        // Should be roughly halfway between (1,0,0) and (0,1,0)
        // i.e., around 45 degrees
        assertEquals(sqrt(2.0) / 2.0, rotated.x, 1e-6)
        assertEquals(sqrt(2.0) / 2.0, rotated.y, 1e-6)
    }

    @Test
    fun `toRotationMatrix matches rotate`() {
        val q = Quaternion.fromAxisAngle(Vector3D.UNIT_Z, PI / 2.0)
        val matrix = q.toRotationMatrix()
        val v = Vector3D(1.0, 0.0, 0.0)

        val matrixRotated = matrix.transform(v)
        val quaternionRotated = q.rotate(v)

        assertEquals(quaternionRotated.x, matrixRotated.x, 1e-5)
        assertEquals(quaternionRotated.y, matrixRotated.y, 1e-5)
        assertEquals(quaternionRotated.z, matrixRotated.z, 1e-5)
    }

    private fun Quaternion.isCloseToQuaternion(other: Quaternion): Boolean =
        abs(w - other.w) < 1e-6 &&
                abs(x - other.x) < 1e-6 &&
                abs(y - other.y) < 1e-6 &&
                abs(z - other.z) < 1e-6
}