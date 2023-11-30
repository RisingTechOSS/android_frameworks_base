/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.filterfw.geometry;

import android.compat.annotation.UnsupportedAppUsage;

import android.util.SparseArray;
import java.util.Map;

/**
 * @hide
 */
public class Point {

    private static final SparseArray<Float> cosCache = new SparseArray<>();
    private static final SparseArray<Float> sinCache = new SparseArray<>();
    
    @UnsupportedAppUsage
    public float x;
    @UnsupportedAppUsage
    public float y;

    @UnsupportedAppUsage
    public Point() {
    }

    @UnsupportedAppUsage
    public Point(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public boolean IsInUnitRange() {
        return x >= 0.0f && x <= 1.0f &&
               y >= 0.0f && y <= 1.0f;
    }

    public Point plus(float x, float y) {
        return new Point(this.x + x, this.y + y);
    }

    public Point plus(Point point) {
        return this.plus(point.x, point.y);
    }

    public Point minus(float x, float y) {
        return new Point(this.x - x, this.y - y);
    }

    public Point minus(Point point) {
        return this.minus(point.x, point.y);
    }

    public Point times(float s) {
        return new Point(this.x * s, this.y * s);
    }

    public Point mult(float x, float y) {
        return new Point(this.x * x, this.y * y);
    }

    public float length() {
        return (float)Math.hypot(x, y);
    }

    public float distanceTo(Point p) {
        return p.minus(this).length();
    }

    public Point scaledTo(float length) {
        return this.times(length / this.length());
    }

    public Point normalize() {
        return this.scaledTo(1.0f);
    }

    public Point rotated90(int count) {
        float nx = this.x;
        float ny = this.y;
        for (int i = 0; i < count; ++i) {
            float ox = nx;
            nx = ny;
            ny = -ox;
        }
        return new Point(nx, ny);
    }

    public Point rotated(float radians) {
        float cosVal, sinVal;
        int key = toSparseArrayKey(radians);

        if (cosCache.get(key) != null) {
            cosVal = cosCache.get(key);
        } else {
            cosVal = (float) Math.cos(radians);
            cosCache.put(key, cosVal);
        }

        if (sinCache.get(key) != null) {
            sinVal = sinCache.get(key);
        } else {
            sinVal = (float) Math.sin(radians);
            sinCache.put(key, sinVal);
        }

        return new Point(cosVal * x - sinVal * y, sinVal * x + cosVal * y);
    }

    private int toSparseArrayKey(float radians) {
        return (int)(radians * 10000);
    }

    public Point rotatedAround(Point center, float radians) {
        return this.minus(center).rotated(radians).plus(center);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
