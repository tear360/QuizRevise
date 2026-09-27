// Générateur d'icônes app (Windows .ico + Linux .png) — exécuter : java tools/GenIco.java
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public class GenIco {
    static final Color PURPLE = new Color(0x6750A4);
    static final Color LAVENDER = new Color(0xC9BFF0);
    static final Color GREEN = new Color(0x1B873B);
    static final Color WHITE = Color.WHITE;

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double r = size * 0.22;
        g.setColor(PURPLE);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, r, r));
        double s = size / 108.0;
        double cardW = 84 * s, cardH = 84 * s, cr = 10 * s;
        g.translate(size / 2.0, size / 2.0);
        g.rotate(Math.toRadians(-8.0));
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(-cardW / 2, -cardH / 2, cardW, cardH, cr, cr));
        g.setColor(LAVENDER);
        g.fill(new RoundRectangle2D.Double(-cardW / 2 + 8 * s, -cardH / 2 + 16 * s, 52 * s, 5 * s, 2.5 * s, 2.5 * s));
        g.fill(new RoundRectangle2D.Double(-cardW / 2 + 8 * s, -cardH / 2 + 27 * s, 38 * s, 5 * s, 2.5 * s, 2.5 * s));
        g.setColor(GREEN);
        g.setStroke(new BasicStroke((float) (6.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-cardW / 2 + 16 * s, cardH / 2 - 24 * s, -cardW / 2 + 30 * s, cardH / 2 - 11 * s));
        g.draw(new Line2D.Double(-cardW / 2 + 30 * s, cardH / 2 - 11 * s, -cardW / 2 + 60 * s, cardH / 2 - 40 * s));
        g.dispose();
        return img;
    }

    /** ICO avec une entrée PNG compressée 256x256 (supporté depuis Windows Vista). */
    static void writeIco(BufferedImage img, File out) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        byte[] data = png.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            // ICONDIR
            fos.write(new byte[]{0, 0, 1, 0, 1, 0});
            // ICONDIRENTRY : 256x256 (0 = 256), 32 bits, PNG
            fos.write(0); fos.write(0);          // width, height
            fos.write(0); fos.write(0);          // palette, réservé
            fos.write(1); fos.write(0);          // plans
            fos.write(32); fos.write(0);         // bpp
            long len = data.length;
            fos.write((int) (len & 0xFF)); fos.write((int) ((len >> 8) & 0xFF));
            fos.write((int) ((len >> 16) & 0xFF)); fos.write((int) ((len >> 24) & 0xFF));
            int offset = 22;
            fos.write(offset & 0xFF); fos.write((offset >> 8) & 0xFF);
            fos.write((offset >> 16) & 0xFF); fos.write((offset >> 24) & 0xFF);
            fos.write(data);
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage img = render(256);
        writeIco(img, new File("desktop/quizrevise.ico"));
        ImageIO.write(img, "png", new File("desktop/quizrevise.png"));
        System.out.println("Icones app generees : desktop/quizrevise.ico + desktop/quizrevise.png");
    }
}
