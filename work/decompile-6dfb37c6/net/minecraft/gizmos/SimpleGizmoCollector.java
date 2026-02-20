package net.minecraft.gizmos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class SimpleGizmoCollector implements GizmoCollector {

    private final List<SimpleGizmoCollector.GizmoInstance> gizmos = new ArrayList();
    private final List<SimpleGizmoCollector.GizmoInstance> temporaryGizmos = new ArrayList();

    public SimpleGizmoCollector() {}

    @Override
    public GizmoProperties add(Gizmo gizmo) {
        SimpleGizmoCollector.GizmoInstance simplegizmocollector_gizmoinstance = new SimpleGizmoCollector.GizmoInstance(gizmo);

        this.gizmos.add(simplegizmocollector_gizmoinstance);
        return simplegizmocollector_gizmoinstance;
    }

    public List<SimpleGizmoCollector.GizmoInstance> drainGizmos() {
        ArrayList<SimpleGizmoCollector.GizmoInstance> arraylist = new ArrayList(this.gizmos);

        arraylist.addAll(this.temporaryGizmos);
        long i = Util.getMillis();

        this.gizmos.removeIf((simplegizmocollector_gizmoinstance) -> {
            return simplegizmocollector_gizmoinstance.getExpireTimeMillis() < i;
        });
        this.temporaryGizmos.clear();
        return arraylist;
    }

    public List<SimpleGizmoCollector.GizmoInstance> getGizmos() {
        return this.gizmos;
    }

    public void addTemporaryGizmos(Collection<SimpleGizmoCollector.GizmoInstance> gizmos) {
        this.temporaryGizmos.addAll(gizmos);
    }

    public static class GizmoInstance implements GizmoProperties {

        private final Gizmo gizmo;
        private boolean isAlwaysOnTop;
        private long startTimeMillis;
        private long expireTimeMillis;
        private boolean shouldFadeOut;

        private GizmoInstance(Gizmo gizmo) {
            this.gizmo = gizmo;
        }

        @Override
        public GizmoProperties setAlwaysOnTop() {
            this.isAlwaysOnTop = true;
            return this;
        }

        @Override
        public GizmoProperties persistForMillis(int milliseconds) {
            this.startTimeMillis = Util.getMillis();
            this.expireTimeMillis = this.startTimeMillis + (long) milliseconds;
            return this;
        }

        @Override
        public GizmoProperties fadeOut() {
            this.shouldFadeOut = true;
            return this;
        }

        public float getAlphaMultiplier(long currentMillis) {
            if (this.shouldFadeOut) {
                long j = this.expireTimeMillis - this.startTimeMillis;
                long k = currentMillis - this.startTimeMillis;

                return 1.0F - Mth.clamp((float) k / (float) j, 0.0F, 1.0F);
            } else {
                return 1.0F;
            }
        }

        public boolean isAlwaysOnTop() {
            return this.isAlwaysOnTop;
        }

        public long getExpireTimeMillis() {
            return this.expireTimeMillis;
        }

        public Gizmo gizmo() {
            return this.gizmo;
        }
    }
}
