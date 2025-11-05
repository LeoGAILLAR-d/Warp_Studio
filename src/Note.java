public class Note {
    int row;
    double start;
    double length;
    int lastPreviewCol = Integer.MIN_VALUE;
    int lastPlayedCycle = Integer.MIN_VALUE;

    public Note(int row, double start, double length) {
        this.row = row;
        this.start = Math.max(0.0, start);
        this.length = Math.max(0.25, length);
    }
}
