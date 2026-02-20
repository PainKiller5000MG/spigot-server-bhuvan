package net.minecraft.server.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TimeUtil;
import org.jspecify.annotations.Nullable;

public class StatsComponent extends JComponent {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("########0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private final int[] values = new int[256];
    private int vp;
    private final @Nullable String[] msgs = new String[11];
    private final MinecraftServer server;
    private final Timer timer;

    public StatsComponent(MinecraftServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(456, 246));
        this.setMinimumSize(new Dimension(456, 246));
        this.setMaximumSize(new Dimension(456, 246));
        this.timer = new Timer(500, (actionevent) -> {
            this.tick();
        });
        this.timer.start();
        this.setBackground(Color.BLACK);
    }

    private void tick() {
        long i = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        this.msgs[0] = "Memory use: " + i / 1024L / 1024L + " mb (" + Runtime.getRuntime().freeMemory() * 100L / Runtime.getRuntime().maxMemory() + "% free)";
        String[] astring = this.msgs;
        DecimalFormat decimalformat = StatsComponent.DECIMAL_FORMAT;
        double d0 = (double) this.server.getAverageTickTimeNanos();

        astring[1] = "Avg tick: " + decimalformat.format(d0 / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND) + " ms";
        this.values[this.vp++ & 255] = (int) (i * 100L / Runtime.getRuntime().maxMemory());
        this.repaint();
    }

    public void paint(Graphics g) {
        g.setColor(new Color(16777215));
        g.fillRect(0, 0, 456, 246);

        for (int i = 0; i < 256; ++i) {
            int j = this.values[i + this.vp & 255];

            g.setColor(new Color(j + 28 << 16));
            g.fillRect(i, 100 - j, 1, j);
        }

        g.setColor(Color.BLACK);

        for (int k = 0; k < this.msgs.length; ++k) {
            String s = this.msgs[k];

            if (s != null) {
                g.drawString(s, 32, 116 + k * 16);
            }
        }

    }

    public void close() {
        this.timer.stop();
    }
}
