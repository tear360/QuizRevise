// Générateur d'icônes PNG legacy (Android < 8.0) — exécuter : java tools/GenIcons.java
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenIcons {
    static final Color PURPLE = new Color(0x6750A4);
    static final Color LAVENDER = new Color(0xC9BFF0);
    static final Color GREEN = new Color(0x1B873B);
    static final Color WHITE = Color.WHITE;

    static void drawCard(Graphics2D g, double s, double cx, double cy) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(-8.0));
        double cardW = 84 * s, cardH = 84 * s, r = 10 * s;
        // Carte blanche arrondie centrée
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(-cardW / 2, -cardH / 2, cardW, cardH, r, r));
        // Deux lignes "texte"
        g.setColor(LAVENDER);
        g.fill(new RoundRectangle2D.Double(-cardW / 2 + 8 * s, -cardH / 2 + 16 * s, 52 * s, 5 * s, 2.5 * s, 2.5 * s));
        g.fill(new RoundRectangle2D.Double(-cardW / 2 + 8 * s, -cardH / 2 + 27 * s, 38 * s, 5 * s, 2.5 * s, 2.5 * s));
        // Coche verte
        g.setColor(GREEN);
        g.setStroke(new BasicStroke((float) (6.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-cardW / 2 + 16 * s, cardH / 2 - 24 * s, -cardW / 2 + 30 * s, cardH / 2 - 11 * s));
        g.draw(new Line2D.Double(-cardW / 2 + 30 * s, cardH / 2 - 11 * s, -cardW / 2 + 60 * s, cardH / 2 - 40 * s));
        g.setTransform(old);
    }

    static BufferedImage square(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(PURPLE);
        g.fillRect(0, 0, size, size);
        drawCard(g, size / 108.0, size / 2.0, size / 2.0);
        g.dispose();
        return img;
    }

    static BufferedImage round(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(PURPLE);
        g.fillOval(0, 0, size, size);
        g.setClip(new Ellipse2D.Double(0, 0, size, size));
        drawCard(g, size / 108.0 * 0.82, size / 2.0, size / 2.0);
        g.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        String[][] densities = {
            {"mdpi", "48"}, {"hdpi", "72"}, {"xhdpi", "96"}, {"xxhdpi", "144"}, {"xxxhdpi", "192"}
        };
        for (String[] d : densities) {
            String dpi = d[0];
            int size = Integer.parseInt(d[1]);
            File dir = new File("app/src/main/res/mipmap-" + dpi);
            dir.mkdirs();
            ImageIO.write(square(size), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(round(size), "png", new File(dir, "ic_launcher_round.png"));
        }
        System.out.println("Icones legacy generees.");
    }
}
