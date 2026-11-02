package dev.madusha.shangos;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;

/**
 * Open-source licences: what the app includes or uses, who made it, under which licence, and the
 * full text.
 */
final class LicensesScreen {
    private final Activity activity;
    private final Ui ui;
    private final Overlays overlays;
    private final ScreenHost host;
    private final IntSupplier settingsRow;
    private TextView infoTitle, infoBody;

    LicensesScreen(
            Activity activity, Ui ui, Overlays overlays, ScreenHost host, IntSupplier settingsRow) {
        this.activity = activity;
        this.ui = ui;
        this.overlays = overlays;
        this.host = host;
        this.settingsRow = settingsRow;
    }

    /** The list; {@code focusRow} is the credit to select. */
    void show(int focusRow) {
        FrameLayout o = overlays.open(0xF20B0B0D);
        overlays.onCancel(() -> host.backToSettings(settingsRow.getAsInt()));
        TextView title = ui.text("Open-source licences", 34, Color.WHITE, "sans-serif-medium");
        title.setLetterSpacing(-.02f);
        o.addView(title, ui.pos(700, 44, 48, 32));
        o.addView(
                ui.line(
                        "This app contains no third-party code libraries. This is everything else"
                                + " it includes or uses.",
                        13,
                        0xC7FFFFFF,
                        "sans-serif"),
                ui.pos(860, 20, 49, 80));
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        o.addView(list, ui.pos(500, 370, 40, 116));
        FrameLayout card = new FrameLayout(activity);
        card.setBackground(ui.shape(0x1AFFFFFF, 22));
        o.addView(card, ui.pos(352, 360, 560, 122));
        infoTitle = ui.text("", 19, Color.WHITE, "sans-serif-medium");
        card.addView(infoTitle, ui.pos(316, 26, 18, 18));
        infoBody = ui.text("", 13, 0xC7FFFFFF, "sans-serif");
        infoBody.setSingleLine(false);
        infoBody.setLineSpacing(ui.px(4), 1);
        card.addView(infoBody, ui.pos(316, 290, 18, 56));
        View[] rows = new View[Credits.ALL.length];
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            final Credits.Credit credit = Credits.ALL[i];
            rows[i] =
                    overlays.valueRow(
                            list,
                            credit.name,
                            () -> credit.spdx,
                            () -> fullText(credit, index),
                            () -> {
                                infoTitle.setText(credit.name);
                                infoBody.setText(
                                        credit.use
                                                + "\n\nBy "
                                                + credit.author
                                                + "\n"
                                                + credit.url
                                                + "\n\nOK shows the full licence.");
                            });
        }
        o.addView(ui.text("Back to close", 12, 0x80FFFFFF, "sans-serif"), ui.pos(300, 16, 49, 500));
        host.post(() -> rows[Math.max(0, Math.min(rows.length - 1, focusRow))].requestFocus());
    }

    /** One licence in full, scrolled with Up and Down. */
    private void fullText(Credits.Credit credit, int index) {
        FrameLayout o = overlays.open(0xF20B0B0D);
        overlays.onCancel(() -> host.post(() -> show(index)));
        TextView title =
                ui.line(credit.name + " · " + credit.spdx, 26, Color.WHITE, "sans-serif-medium");
        o.addView(title, ui.pos(860, 34, 48, 30));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackground(ui.shape(0x14FFFFFF, 18));
        scroll.setPadding(ui.px(20), ui.px(16), ui.px(20), ui.px(16));
        scroll.setClipToPadding(false);
        scroll.setContentDescription(credit.name + " licence text");
        TextView text = ui.text(read(credit.text), 12, 0xD9FFFFFF, "sans-serif");
        text.setSingleLine(false);
        text.setLineSpacing(ui.px(3), 1);
        scroll.addView(text);
        o.addView(scroll, ui.pos(864, 392, 48, 84));
        o.addView(
                ui.text("Up and Down to read · Back to return", 12, 0x80FFFFFF, "sans-serif"),
                ui.pos(500, 16, 49, 500));
        host.post(scroll::requestFocus);
    }

    private String read(int rawResource) {
        try (InputStream in = activity.getResources().openRawResource(rawResource)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException | RuntimeException e) {
            return "The licence text could not be read. It is also in the source: LICENSE and"
                    + " NOTICE.md.";
        }
    }
}
