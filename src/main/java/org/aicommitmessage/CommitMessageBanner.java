package org.aicommitmessage;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 菜单中的紧凑横幅：背景左右铺满，中央叠加插件原有的 M 图标。 */
final class CommitMessageBanner extends JPanel {
    private final Icon background = IconLoader.getIcon("/images/commit-banner.jpg", CommitMessageBanner.class);
    // 横幅始终使用深色背景，因此固定使用现有白色图标，保持明暗主题下的对比度。
    private final Icon brand = IconLoader.getIcon("/icons/commitMessage_dark.svg", CommitMessageBanner.class);

    /** 固定横幅逻辑高度，宽度交给菜单布局决定，不添加左右边距。 */
    CommitMessageBanner() {
        setOpaque(false);
        setPreferredSize(JBUI.size(0, 48)); // 不用图片原始尺寸撑大弹出菜单。
        setMinimumSize(new Dimension(0, JBUI.scale(48)));
        getAccessibleContext().setAccessibleName("AI Commit Message：将代码变更转换为 Git 提交信息");
    }

    /** 等比覆盖横幅区域，并在中央使用原生 SVG 绘制清晰的品牌标识。 */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = Math.max((double) getWidth() / background.getIconWidth(),
                    (double) getHeight() / background.getIconHeight());
            Graphics2D imageCanvas = (Graphics2D) canvas.create();
            try {
                imageCanvas.translate((getWidth() - background.getIconWidth() * scale) / 2,
                        (getHeight() - background.getIconHeight() * scale) / 2);
                imageCanvas.scale(scale, scale); // 保持比例，通过上下裁切让背景左右通铺。
                background.paintIcon(this, imageCanvas, 0, 0);
            } finally {
                imageCanvas.dispose();
            }
            int badgeSize = JBUI.scale(24);
            int badgeX = (getWidth() - badgeSize) / 2;
            int badgeY = (getHeight() - badgeSize) / 2;
            canvas.setColor(new Color(0x17283E));
            canvas.fillOval(badgeX, badgeY, badgeSize, badgeSize); // 覆盖原图中央星形，使 M 成为视觉中心。
            Icon icon = IconUtil.scale(brand, this, (float) JBUI.scale(20) / brand.getIconWidth());
            icon.paintIcon(this, canvas, (getWidth() - icon.getIconWidth()) / 2,
                    (getHeight() - icon.getIconHeight()) / 2); // 矢量图标独立绘制，避免缩小后模糊。
        } finally {
            canvas.dispose();
        }
    }
}
