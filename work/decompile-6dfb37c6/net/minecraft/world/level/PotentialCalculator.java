package net.minecraft.world.level;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;

public class PotentialCalculator {

    private final List<PotentialCalculator.PointCharge> charges = Lists.newArrayList();

    public PotentialCalculator() {}

    public void addCharge(BlockPos pos, double charge) {
        if (charge != 0.0D) {
            this.charges.add(new PotentialCalculator.PointCharge(pos, charge));
        }

    }

    public double getPotentialEnergyChange(BlockPos pos, double charge) {
        if (charge == 0.0D) {
            return 0.0D;
        } else {
            double d1 = 0.0D;

            for (PotentialCalculator.PointCharge potentialcalculator_pointcharge : this.charges) {
                d1 += potentialcalculator_pointcharge.getPotentialChange(pos);
            }

            return d1 * charge;
        }
    }

    private static class PointCharge {

        private final BlockPos pos;
        private final double charge;

        public PointCharge(BlockPos pos, double charge) {
            this.pos = pos;
            this.charge = charge;
        }

        public double getPotentialChange(BlockPos pos) {
            double d0 = this.pos.distSqr(pos);

            return d0 == 0.0D ? Double.POSITIVE_INFINITY : this.charge / Math.sqrt(d0);
        }
    }
}
