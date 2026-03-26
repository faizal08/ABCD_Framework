package com.eit.automation.utils;

import org.monte.media.Format;
import org.monte.media.math.Rational;
import org.monte.screenrecorder.ScreenRecorder;
import static org.monte.media.AudioFormatKeys.*;
import static org.monte.media.VideoFormatKeys.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class VideoRecorder extends ScreenRecorder {

    private String fileName;

    /**
     * NO-ARGUMENT CONSTRUCTOR
     * This is required for your 'private static VideoRecorder videoRecorder = new VideoRecorder();'
     * call in Main.java to work.
     */
    public VideoRecorder() throws IOException, AWTException {
        super(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration(),
                new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()),
                new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                        CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE, DepthKey, 24, FrameRateKey, Rational.valueOf(15),
                        QualityKey, 1.0f, KeyFrameIntervalKey, 15 * 60),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, "black", FrameRateKey, Rational.valueOf(30)),
                null, new File("temp"));
    }

    /**
     * FULL CONSTRUCTOR
     * Used internally by the startRecording method.
     */
    public VideoRecorder(GraphicsConfiguration cfg, Rectangle captureArea, Format fileFormat,
                         Format screenFormat, Format mouseFormat, Format audioFormat, File movieFolder, String fileName)
            throws IOException, AWTException {
        super(cfg, captureArea, fileFormat, screenFormat, mouseFormat, audioFormat, movieFolder);
        this.fileName = fileName;
    }

    /**
     * Overriding this method is CRITICAL to use your custom filename
     * instead of the default Monte timestamped name.
     */
    @Override
    protected File createMovieFile(Format fileFormat) throws IOException {
        if (!movieFolder.exists()) {
            movieFolder.mkdirs();
        }
        return new File(movieFolder, fileName);
    }

    // Static instance to manage the current active recording session
    private static VideoRecorder activeRecorder;

    /**
     * Initializes and starts a new recording session.
     */
    public void startRecording(String directory, String fileName) throws Exception {
        File file = new File(directory);

        GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();

        // Create a new instance for this specific test case execution
        activeRecorder = new VideoRecorder(gc, gc.getBounds(),
                new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                        CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE, DepthKey, 24, FrameRateKey, Rational.valueOf(15),
                        QualityKey, 1.0f, KeyFrameIntervalKey, 15 * 60),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, "black", FrameRateKey, Rational.valueOf(30)),
                null, file, fileName);

        activeRecorder.start();
    }

    /**
     * Stops the active recording session.
     */
    public void stopRecording() throws Exception {
        if (activeRecorder != null) {
            activeRecorder.stop();
            activeRecorder = null; // Reset for next use
        }
    }
}