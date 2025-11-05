import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class MainFrame extends JFrame {
    private final int NUM_MEASURES = 16;
    private String[] NOTES;
    private final List<Note> notes = new ArrayList<>();
    private final List<Note> selectedNotes = new ArrayList<>();
    private final List<Note> clipboard = new ArrayList<>();

    private PianoRollPanel pianoRoll;
    private MidiManager midiManager;
    private volatile boolean isPlaying = false;
    private int playCycle = 0;
    private final int DEFAULT_BPM = 120;
    private int BPM = DEFAULT_BPM;
    private int msPerBeat = 60000 / BPM;

    private JButton playBtn;
    private JButton stopBtn;
    private JSpinner bpmSpinner;
    private JToggleButton previewToggle;
    private JToggleButton loopBtn;
    private volatile boolean loopEnabled = false;

    private JScrollPane scrollPane;
    private JToggleButton metronomeToggle;

    public MainFrame() {
        NOTES = buildNoteNames(1, 6);
        reverseArray(NOTES);

        setTitle("Mini Piano Roll");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        try { midiManager = new MidiManager(); } catch (Exception e) { e.printStackTrace(); }

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        playBtn = new JButton("Play");
        stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);
        toolbar.add(playBtn);
        toolbar.add(stopBtn);

        toolbar.addSeparator();
        toolbar.add(new JLabel("BPM:"));
        bpmSpinner = new JSpinner(new SpinnerNumberModel(BPM, 20, 300, 1));
        toolbar.add(bpmSpinner);

        JSlider zoomSlider = new JSlider(20, 120, 60);
        zoomSlider.setPreferredSize(new Dimension(150, 20));
        toolbar.addSeparator();
        toolbar.add(new JLabel("Zoom:"));
        toolbar.add(zoomSlider);

        toolbar.addSeparator();
        JButton clearBtn = new JButton("Clear");
        toolbar.add(clearBtn);

        previewToggle = new JToggleButton("Preview on place/select");
        toolbar.addSeparator();
        toolbar.add(previewToggle);

        metronomeToggle = new JToggleButton("Metronome");
        toolbar.addSeparator();
        toolbar.add(metronomeToggle);

        loopBtn = new JToggleButton("Loop");
        toolbar.addSeparator();
        toolbar.add(loopBtn);
        loopBtn.addActionListener(e -> loopEnabled = loopBtn.isSelected());

        add(toolbar, BorderLayout.NORTH);

        pianoRoll = new PianoRollPanel();
        scrollPane = new JScrollPane(pianoRoll, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        pianoRoll.addMouseWheelListener(e -> handleMouseWheel(e));

        playBtn.addActionListener(e -> startPlayback((Integer) bpmSpinner.getValue()));
        stopBtn.addActionListener(e -> stopPlayback());
        clearBtn.addActionListener(e -> { synchronized (notes) { notes.clear(); selectedNotes.clear(); } pianoRoll.revalidate(); pianoRoll.repaint(); });
        zoomSlider.addChangeListener(e -> { pianoRoll.setCellWidth(zoomSlider.getValue()); pianoRoll.revalidate(); pianoRoll.repaint(); });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "togglePlay");
        getRootPane().getActionMap().put("togglePlay", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { if (isPlaying) stopPlayback(); else startPlayback((Integer)bpmSpinner.getValue()); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "bpmUp");
        getRootPane().getActionMap().put("bpmUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { bpmSpinner.setValue((Integer)bpmSpinner.getValue() + 1); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "bpmDown");
        getRootPane().getActionMap().put("bpmDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { bpmSpinner.setValue((Integer)bpmSpinner.getValue() - 1); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        getRootPane().getActionMap().put("copy", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { synchronized (notes) { clipboard.clear(); for (Note n : selectedNotes) clipboard.add(new Note(n.row, n.start, n.length)); } } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        getRootPane().getActionMap().put("paste", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { synchronized (notes) { if (clipboard.isEmpty()) return; double offset = 1.0; List<Note> pasted = new ArrayList<>(); for (Note c : clipboard) { double newStart = Math.min(NUM_MEASURES - c.length, c.start + offset); Note copy = new Note(c.row, newStart, c.length); notes.add(copy); pasted.add(copy); } selectedNotes.clear(); selectedNotes.addAll(pasted); if (previewToggle.isSelected() && !pasted.isEmpty()) playPreviewNotes(pasted, 300); } pianoRoll.revalidate(); pianoRoll.repaint(); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        getRootPane().getActionMap().put("cut", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { synchronized (notes) { clipboard.clear(); for (Note n : selectedNotes) clipboard.add(new Note(n.row, n.start, n.length)); notes.removeAll(selectedNotes); selectedNotes.clear(); } pianoRoll.revalidate(); pianoRoll.repaint(); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getRootPane().getActionMap().put("delete", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { synchronized (notes) { notes.removeAll(selectedNotes); selectedNotes.clear(); } pianoRoll.revalidate(); pianoRoll.repaint(); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
        getRootPane().getActionMap().put("duplicate", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { duplicateSelection(); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK), "octaveUp");
        getRootPane().getActionMap().put("octaveUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { octaveShiftSelected(-12); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK), "octaveDown");
        getRootPane().getActionMap().put("octaveDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { octaveShiftSelected(12); } });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "moveLeft");
        getRootPane().getActionMap().put("moveLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveSelectionBy(-1, 0); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "moveRight");
        getRootPane().getActionMap().put("moveRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveSelectionBy(1, 0); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "moveUp");
        getRootPane().getActionMap().put("moveUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveSelectionBy(0, -1); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "moveDown");
        getRootPane().getActionMap().put("moveDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveSelectionBy(0, 1); } });

        setVisible(true);

        SwingUtilities.invokeLater(() -> centerOnNoteName("C3"));
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        if (e.isControlDown() && e.isAltDown()) {
            int steps = (int)Math.signum(-e.getPreciseWheelRotation());
            int deltaH = steps * 4;
            int newH = Math.max(12, Math.min(80, pianoRoll.getCellHeight() + deltaH));
            pianoRoll.setCellHeight(newH);
            pianoRoll.setPreferredSize(new Dimension(NUM_MEASURES * pianoRoll.getCellWidth() + 100, NOTES.length * pianoRoll.getCellHeight()));
            pianoRoll.revalidate();
            pianoRoll.repaint();
        } else if (e.isControlDown()) {
            int steps = (int)Math.signum(-e.getPreciseWheelRotation());
            int delta = steps * 6;
            JViewport vpView = scrollPane.getViewport();
            Rectangle viewRect = vpView.getViewRect();
            Dimension prefBefore = pianoRoll.getPreferredSize();
            double centerRel = prefBefore.height > 0 ? (double)(viewRect.y + viewRect.height / 2) / (double)prefBefore.height : 0.5;
            int newW = Math.max(20, pianoRoll.getCellWidth() + delta);
            pianoRoll.setCellWidth(newW);
            pianoRoll.setPreferredSize(new Dimension(NUM_MEASURES * pianoRoll.getCellWidth() + 100, NOTES.length * pianoRoll.getCellHeight()));
            pianoRoll.revalidate();
            pianoRoll.repaint();
            SwingUtilities.invokeLater(() -> {
                Dimension prefAfter = pianoRoll.getPreferredSize();
                int newCenterY = Math.max(0, (int)(centerRel * prefAfter.height));
                int newY = Math.max(0, newCenterY - vpView.getHeight() / 2);
                vpView.setViewPosition(new Point(viewRect.x, newY));
            });
            e.consume();
        } else {
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            int units = e.getUnitsToScroll();
            int amount = (int)Math.round(units * vsb.getUnitIncrement() * (e.getPreciseWheelRotation() == 0 ? 1 : Math.abs(e.getPreciseWheelRotation())));
            vsb.setValue(vsb.getValue() + amount);
        }
    }

    private static String[] buildNoteNames(int lowOctave, int highOctave) {
        String[] scale = new String[] {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        List<String> list = new ArrayList<>();
        for (int o = lowOctave; o <= highOctave; o++) for (String s : scale) list.add(s + o);
        return list.toArray(new String[0]);
    }

    private static void reverseArray(String[] arr) {
        for (int i = 0, j = arr.length - 1; i < j; i++, j--) { String t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
    }

    private void centerOnNoteName(String noteName) {
        if (NOTES == null || scrollPane == null) return;
        int row = -1;
        for (int i = 0; i < NOTES.length; i++) if (NOTES[i].equalsIgnoreCase(noteName)) { row = i; break; }
        if (row < 0) return;
        Rectangle view = scrollPane.getViewport().getViewRect();
        int ch = pianoRoll.getCellHeight();
        int y = row * ch;
        int centerY = Math.max(0, y - (view.height / 2) + (ch / 2));
        scrollPane.getViewport().setViewPosition(new Point(0, centerY));
    }

    private void duplicateSelection() {
        synchronized (notes) {
            if (selectedNotes.isEmpty()) return;
            double minStart = Double.POSITIVE_INFINITY;
            double maxEnd = Double.NEGATIVE_INFINITY;
            for (Note s : selectedNotes) { minStart = Math.min(minStart, s.start); maxEnd = Math.max(maxEnd, s.start + s.length); }
            double offset = maxEnd - minStart;
            List<Note> newCopies = new ArrayList<>();
            for (Note s : new ArrayList<>(selectedNotes)) {
                double rel = s.start - minStart;
                double newStart = Math.min(NUM_MEASURES - s.length, maxEnd + rel);
                Note copy = new Note(s.row, newStart, s.length);
                notes.add(copy);
                newCopies.add(copy);
            }
            selectedNotes.clear();
            selectedNotes.addAll(newCopies);
            if (previewToggle != null && previewToggle.isSelected() && !newCopies.isEmpty()) playPreviewNotes(newCopies, 300);
        }
        pianoRoll.revalidate();
        pianoRoll.repaint();
    }

    private void octaveShiftSelected(int deltaRows) {
        synchronized (notes) {
            if (selectedNotes.isEmpty()) return;
            for (Note s : selectedNotes) {
                int newRow = s.row + deltaRows;
                if (newRow < 0) newRow = 0;
                if (newRow >= NOTES.length) newRow = NOTES.length - 1;
                s.row = newRow;
            }
            if (previewToggle != null && previewToggle.isSelected()) playPreviewNotes(new ArrayList<>(selectedNotes), 300);
        }
        pianoRoll.revalidate();
        pianoRoll.repaint();
    }

    private void moveSelectionBy(int deltaCols, int deltaRows) {
        List<Note> previewList = new ArrayList<>();
        synchronized (notes) {
            if (selectedNotes.isEmpty()) return;
            for (Note s : selectedNotes) {
                int baseCol = (int)Math.round(s.start);
                double newStart = baseCol + deltaCols;
                newStart = Math.max(0.0, Math.min(NUM_MEASURES - s.length, newStart));
                s.start = newStart;
                int newRow = s.row + deltaRows;
                if (newRow < 0) newRow = 0;
                if (newRow >= NOTES.length) newRow = NOTES.length - 1;
                s.row = newRow;
                s.lastPreviewCol = (int)Math.floor(s.start + 1e-6);
                previewList.add(s);
            }
        }
        if (previewToggle != null && previewToggle.isSelected() && !previewList.isEmpty()) playPreviewNotes(previewList, 150);
        pianoRoll.revalidate();
        pianoRoll.repaint();
    }

    private synchronized void startPlayback(int bpm) {
        if (isPlaying) return;
        BPM = bpm;
        msPerBeat = 60000 / BPM;
        isPlaying = true;
        playBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        playCycle++;
        new Thread(this::playWithCursor).start();
    }

    private synchronized void stopPlayback() {
        if (!isPlaying) return;
        isPlaying = false;
        if (midiManager != null) midiManager.allNotesOff();
        playBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void playWithCursor() {
        final double EPS = 1e-6;
        long totalMs = (long)(NUM_MEASURES * msPerBeat);
        long t0 = System.currentTimeMillis();
        double lastPos = -EPS;
        while (isPlaying) {
            long now = System.currentTimeMillis();
            long elapsed = now - t0;
            if (elapsed >= totalMs) {
                if (loopEnabled) { playCycle++; t0 = System.currentTimeMillis(); lastPos = -EPS; } else break;
            }
            double pos = elapsed / (double) msPerBeat;
            pianoRoll.setPlayHeadPos(pos);
            int lastMeasure = (int)Math.floor(lastPos);
            int curMeasure = (int)Math.floor(pos);
            if (metronomeToggle != null && metronomeToggle.isSelected()) {
                for (int m = lastMeasure + 1; m <= curMeasure; m++) {
                    boolean accent = (m % 4 == 0);
                    if (midiManager != null) midiManager.playMetronomeTick(accent);
                }
            }
            synchronized (notes) {
                for (Note n : notes) {
                    if (n.lastPlayedCycle != playCycle && (n.start + EPS) >= lastPos && n.start <= (pos + EPS)) {
                        n.lastPlayedCycle = playCycle;
                        if (midiManager != null) {
                            int midi = midiManager.noteNameToMidi(NOTES[n.row]);
                            if (midi >= 0) midiManager.playNoteAsync(midi, (int)Math.round(n.length * msPerBeat), 90);
                        }
                    }
                }
            }
            lastPos = pos;
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        }
        pianoRoll.setPlayHeadPos(-1);
        isPlaying = false;
        SwingUtilities.invokeLater(() -> { playBtn.setEnabled(true); stopBtn.setEnabled(false); });
    }

    private void playPreviewNotes(List<Note> noteList, int durationMs) {
        if (midiManager == null || noteList == null || noteList.isEmpty()) return;
        List<Integer> midiNotes = new ArrayList<>();
        for (Note n : noteList) {
            if (n.row < 0 || n.row >= NOTES.length) continue;
            int midi = midiManager.noteNameToMidi(NOTES[n.row]);
            if (midi >= 0) midiNotes.add(midi);
        }
        midiManager.playPreviewNotes(midiNotes, durationMs);
    }

    private int noteNameToMidi(String name) {
        if (midiManager == null) return -1;
        return midiManager.noteNameToMidi(name);
    }

    private void handleNoteCreationPreview(Note n) {
        if (previewToggle.isSelected()) playPreviewNote(n, 300);
    }

    private void playPreviewNote(Note n, int durationMs) {
        List<Note> tmp = new ArrayList<>(); tmp.add(n); playPreviewNotes(tmp, durationMs);
    }

    private class PianoRollPanel extends JPanel {
        private int cellWidth = 60;
        private int cellHeight = 30;
        private final int SUBDIV_SHOW_THRESHOLD = 80;
        private double playHeadPos = -1.0;

        private Note dragAnchorNote = null;
        private boolean dragging = false;
        private boolean resizing = false;
        private int dragMouseStartX, dragMouseStartY;
        private final List<Double> selectionInitialStarts = new ArrayList<>();
        private final List<Integer> selectionInitialRows = new ArrayList<>();

        private boolean marqueeActive = false;
        private Rectangle marqueeRect = new Rectangle();

        private final double MIN_LENGTH_COLS = 0.25;

        public PianoRollPanel() {
            setBackground(new Color(30, 30, 30));
            setPreferredSize(new Dimension(NUM_MEASURES * cellWidth + 100, NOTES.length * cellHeight));
            setFocusable(true);

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    int x = e.getX(), y = e.getY();
                    Note hit = findNoteAtPixel(x, y);
                    boolean ctrl = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                    if (hit != null && SwingUtilities.isLeftMouseButton(e)) {
                        synchronized (notes) {
                            if (ctrl) {
                                if (selectedNotes.contains(hit)) selectedNotes.remove(hit);
                                else selectedNotes.add(hit);
                            } else {
                                if (!selectedNotes.contains(hit)) { selectedNotes.clear(); selectedNotes.add(hit); }
                            }
                        }
                        Rectangle r = noteRect(hit);
                        if (Math.abs(x - (r.x + r.width)) <= 8) { resizing = true; dragAnchorNote = hit; }
                        else {
                            dragging = true; dragAnchorNote = hit; dragMouseStartX = x; dragMouseStartY = y; selectionInitialStarts.clear(); selectionInitialRows.clear(); synchronized (notes) { for (Note s : selectedNotes) { selectionInitialStarts.add(s.start); selectionInitialRows.add(s.row); } }
                        }
                        repaint();
                        if (previewToggle.isSelected()) { List<Note> toPreview = new ArrayList<>(); synchronized (notes) { toPreview.addAll(selectedNotes); } if (!toPreview.isEmpty()) playPreviewNotes(toPreview, 300); }
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        marqueeActive = true; marqueeRect.setBounds(x, y, 0, 0); if (!ctrl) { synchronized (notes) { selectedNotes.clear(); } }
                    }
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (marqueeActive) {
                        Rectangle sel = new Rectangle(marqueeRect);
                        if (sel.width < 0) { sel.x += sel.width; sel.width = -sel.width; }
                        if (sel.height < 0) { sel.y += sel.height; sel.height = -sel.height; }
                        sel.grow(6, 6);
                        synchronized (notes) { for (Note n : notes) if (noteRect(n).intersects(sel)) { if (!selectedNotes.contains(n)) selectedNotes.add(n); } }
                        if (previewToggle.isSelected()) { List<Note> toPreview = new ArrayList<>(); synchronized (notes) { toPreview.addAll(selectedNotes); } if (!toPreview.isEmpty()) playPreviewNotes(toPreview, 300); }
                    }
                    if (dragAnchorNote != null) {
                        synchronized (notes) {
                            if (resizing) {
                                snapNoteToGrid(dragAnchorNote);
                                List<Note> toRemove = new ArrayList<>(); double a1 = dragAnchorNote.start; double b1 = dragAnchorNote.start + dragAnchorNote.length; for (Note n : notes) { if (n == dragAnchorNote) continue; if (n.row != dragAnchorNote.row) continue; double a2 = n.start; double b2 = n.start + n.length; if (a2 < b1 && b2 > a1) toRemove.add(n); } if (!toRemove.isEmpty()) { notes.removeAll(toRemove); selectedNotes.removeAll(toRemove); }
                            } else if (dragging) {
                                for (Note s : selectedNotes) { snapNoteToGrid(s); List<Note> toRemove2 = new ArrayList<>(); double a1 = s.start; double b1 = s.start + s.length; for (Note n : notes) { if (n == s) continue; if (selectedNotes.contains(n)) continue; if (n.row != s.row) continue; double a2 = n.start; double b2 = n.start + n.length; if (a2 < b1 && b2 > a1) toRemove2.add(n); } if (!toRemove2.isEmpty()) { notes.removeAll(toRemove2); selectedNotes.removeAll(toRemove2); } }
                            }
                        }
                    }
                    marqueeActive = false; dragging = false; resizing = false; dragAnchorNote = null; repaint();
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        int x = e.getX(), y = e.getY(); int col = xToCol(x), row = yToRow(y); Note hit = findNoteAtCell(row, col); synchronized (notes) { if (hit != null) { notes.remove(hit); selectedNotes.remove(hit); repaint(); } else if (validCell(row, col)) { Note n = new Note(row, col, 1.0); notes.add(n); selectedNotes.clear(); selectedNotes.add(n); repaintCell(row, col); handleNoteCreationPreview(n); } }
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() { @Override public void mouseDragged(MouseEvent e) {
                int x = e.getX(), y = e.getY(); if (marqueeActive) { int rx = Math.min(marqueeRect.x, x); int ry = Math.min(marqueeRect.y, y); int rw = Math.abs(x - marqueeRect.x); int rh = Math.abs(y - marqueeRect.y); marqueeRect.setBounds(rx, ry, rw, rh); repaint(); return; } if (resizing && dragAnchorNote != null) { int baseX = 100 + (int)Math.round(dragAnchorNote.start * cellWidth); double newWidthPx = x - baseX; double newLenCols = Math.max(MIN_LENGTH_COLS, newWidthPx / (double)cellWidth); newLenCols = Math.min(newLenCols, NUM_MEASURES - dragAnchorNote.start); dragAnchorNote.length = newLenCols; snapNoteToGrid(dragAnchorNote); synchronized (notes) { List<Note> toRemove = new ArrayList<>(); double a1 = dragAnchorNote.start; double b1 = dragAnchorNote.start + dragAnchorNote.length; for (Note n : notes) { if (n == dragAnchorNote) continue; if (n.row != dragAnchorNote.row) continue; double a2 = n.start; double b2 = n.start + n.length; if (a2 < b1 && b2 > a1) toRemove.add(n); } if (!toRemove.isEmpty()) { notes.removeAll(toRemove); selectedNotes.removeAll(toRemove); } } repaint(); return; } if (dragging && dragAnchorNote != null) { double deltaCols = (x - dragMouseStartX) / (double)cellWidth; int deltaRows = yToRow(y) - yToRow(dragMouseStartY); synchronized (notes) { for (int i = 0; i < selectedNotes.size(); i++) { Note s = selectedNotes.get(i); double initialStart = selectionInitialStarts.get(i); int initialRow = selectionInitialRows.get(i); double newStart = initialStart + deltaCols; newStart = Math.max(0.0, Math.min(NUM_MEASURES - s.length, newStart)); s.start = newStart; int newRow = Math.max(0, Math.min(NOTES.length - 1, initialRow + deltaRows)); s.row = newRow; int currCol = (int)Math.floor(s.start + 1e-6); if (showSubdivisions()) { int halfCol = (int)Math.floor(s.start * 2.0 + 1e-6); if (s.lastPreviewCol != halfCol) { s.lastPreviewCol = halfCol; if (previewToggle != null && previewToggle.isSelected()) playPreviewNote(s, 120); } } else { if (s.lastPreviewCol != currCol) { s.lastPreviewCol = currCol; if (previewToggle != null && previewToggle.isSelected()) playPreviewNote(s, 120); } } } } repaint(); } } });
        }

        public void setCellWidth(int w) { this.cellWidth = Math.max(20, w); setPreferredSize(new Dimension(NUM_MEASURES * cellWidth + 100, NOTES.length * cellHeight)); revalidate(); }
        public int getCellHeight() { return cellHeight; }
        public void setCellHeight(int h) { this.cellHeight = Math.max(12, Math.min(80, h)); setPreferredSize(new Dimension(NUM_MEASURES * cellWidth + 100, NOTES.length * cellHeight)); revalidate(); }
        private boolean showSubdivisions() { return cellWidth >= SUBDIV_SHOW_THRESHOLD; }
        public void setPlayHeadPos(double pos) { this.playHeadPos = pos; repaint(); }
        public void setPlayHead(int col) { if (col < 0) this.playHeadPos = -1.0; else this.playHeadPos = col; repaint(); }
        private int getSnapThreshold() { return Math.max(6, cellWidth / 2); }
        private void snapNoteToGrid(Note n) { int SNAP_THRESHOLD = getSnapThreshold(); if (showSubdivisions()) { double startPx = n.start * cellWidth; double nearestHalfCol = Math.round(n.start * 2.0) / 2.0; if (Math.abs(startPx - nearestHalfCol * cellWidth) <= SNAP_THRESHOLD) n.start = nearestHalfCol; double endCols = n.start + n.length; double endPx = endCols * cellWidth; double nearestHalfEnd = Math.round(endCols * 2.0) / 2.0; if (Math.abs(endPx - nearestHalfEnd * cellWidth) <= SNAP_THRESHOLD) n.length = Math.max(MIN_LENGTH_COLS, nearestHalfEnd - n.start); } else { double startPx = n.start * cellWidth; double nearestStartCol = Math.round(n.start); if (Math.abs(startPx - nearestStartCol * cellWidth) <= SNAP_THRESHOLD) n.start = nearestStartCol; double endCols = n.start + n.length; double endPx = endCols * cellWidth; double nearestEndCol = Math.round(endCols); if (Math.abs(endPx - nearestEndCol * cellWidth) <= SNAP_THRESHOLD) n.length = Math.max(MIN_LENGTH_COLS, nearestEndCol - n.start); } if (n.start < 0) n.start = 0; if (n.start + n.length > NUM_MEASURES) n.length = NUM_MEASURES - n.start; }
        public int getCellWidth() { return cellWidth; }
        public void repaintCell(int row, int col) { if (row < 0 || row >= NOTES.length || col < 0 || col >= NUM_MEASURES) return; int x = 100 + col * cellWidth; int y = row * cellHeight; int margin = 12; repaint(x - margin, y - margin, cellWidth + margin * 2, cellHeight + margin * 2); }
        private int xToCol(int x) { return Math.max(0, (x - 100) / cellWidth); }
        private int yToRow(int y) { return Math.max(0, y / cellHeight); }
        private boolean validCell(int row, int col) { return row >= 0 && row < NOTES.length && col >= 0 && col < NUM_MEASURES; }
        private Note findNoteAtPixel(int x, int y) { int row = yToRow(y); synchronized (notes) { for (Note n : notes) { if (n.row != row) continue; Rectangle r = noteRect(n); if (r.contains(x, y)) return n; } } return null; }
        private Note findNoteAtCell(int row, int col) { synchronized (notes) { for (Note n : notes) { if (n.row == row && col >= Math.floor(n.start) && col < Math.ceil(n.start + n.length)) return n; } } return null; }
        private Rectangle noteRect(Note n) { int x = 100 + (int)Math.round(n.start * cellWidth); int y = n.row * cellHeight; int w = (int)Math.round(n.length * cellWidth); int h = cellHeight; return new Rectangle(x + 4, y + 4, Math.max(6, w - 8), h - 8); }
        @Override public Dimension getPreferredSize() { return new Dimension(100 + NUM_MEASURES * cellWidth, NOTES.length * cellHeight); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(getBackground()); g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(50, 50, 50)); g2.fillRect(0, 0, 100, getHeight()); g2.setColor(Color.WHITE); g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            for (int r = 0; r < NOTES.length; r++) { int y = r * cellHeight; if ((r % 2) == 0) g2.setColor(new Color(70, 70, 70)); else g2.setColor(new Color(60, 60, 60)); g2.fillRect(0, y, 100, cellHeight); g2.setColor(Color.WHITE); g2.drawString(NOTES[r], 8, y + cellHeight / 2 + 5); }
            for (int c = 0; c < NUM_MEASURES; c++) { int x = 100 + c * cellWidth; boolean isDownbeat = (c % 4 == 0); for (int r = 0; r < NOTES.length; r++) { int y = r * cellHeight; if (isDownbeat) g2.setColor(new Color(50, 50, 60)); else g2.setColor(new Color(40, 40, 40)); g2.fillRect(x + 1, y + 1, cellWidth - 2, cellHeight - 2); g2.setColor(new Color(80, 80, 80)); g2.drawRect(x, y, cellWidth, cellHeight); } if (isDownbeat) { g2.setColor(new Color(200, 100, 100, 200)); g2.setStroke(new BasicStroke(2f)); } else { g2.setColor(new Color(120, 120, 120, 140)); g2.setStroke(new BasicStroke(1f)); } g2.drawLine(100 + c * cellWidth, 0, 100 + c * cellWidth, NOTES.length * cellHeight); }
            synchronized (notes) { for (Note n : notes) { Rectangle r = noteRect(n); boolean isSelected = selectedNotes.contains(n); if (isSelected) g2.setColor(new Color(255, 220, 100)); else g2.setColor(new Color(120, 200, 255)); g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6); g2.setColor(isSelected ? new Color(180, 120, 10) : new Color(20, 80, 120)); g2.setStroke(new BasicStroke(2f)); g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6); g2.setColor(Color.WHITE); String label = NOTES[n.row]; g2.drawString(label, r.x + 6, r.y + r.height / 2 + 5); g2.setColor(new Color(255,255,255,140)); g2.fillRect(r.x + r.width - 6, r.y + r.height/2 - 6, 6, 12); } }
            if (marqueeActive) { g2.setColor(new Color(100, 150, 255, 80)); g2.fillRect(marqueeRect.x, marqueeRect.y, marqueeRect.width, marqueeRect.height); g2.setColor(new Color(100, 150, 255, 160)); g2.setStroke(new BasicStroke(1f)); g2.drawRect(marqueeRect.x, marqueeRect.y, marqueeRect.width, marqueeRect.height); }
            if (playHeadPos >= 0.0) { double px = 100 + playHeadPos * cellWidth; int xline = (int)Math.round(px); g2.setColor(new Color(255, 80, 80, 200)); g2.setStroke(new BasicStroke(3f)); g2.drawLine(xline, 0, xline, NOTES.length * cellHeight); }
            if (showSubdivisions()) { g2.setColor(new Color(90, 90, 100)); g2.setStroke(new BasicStroke(1f)); for (int c = 0; c < NUM_MEASURES * 2; c++) { double cx = 100 + (c * (cellWidth / 2.0)); g2.drawLine((int)Math.round(cx), 0, (int)Math.round(cx), NOTES.length * cellHeight); } }
            g2.dispose();
        }
    }
}
