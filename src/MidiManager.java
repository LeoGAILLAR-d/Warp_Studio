import javax.sound.midi.*;
import java.util.List;

public class MidiManager {
    private Synthesizer synth;
    private MidiChannel channel;
    private MidiChannel percussionChannel;

    public MidiManager() throws MidiUnavailableException {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        MidiChannel[] channels = synth.getChannels();
        if (channels != null && channels.length > 0) {
            channel = channels[0];
            channel.programChange(0);
            if (channels.length > 9) percussionChannel = channels[9];
        }
    }

    public void allNotesOff() {
        if (channel != null) {
            try { channel.allNotesOff(); } catch (Exception ignored) {}
        }
    }

    public void playNoteAsync(int midi, int durationMs, int velocity) {
        if (channel == null || midi < 0) return;
        channel.noteOn(midi, velocity);
        new Thread(() -> {
            try { Thread.sleep(durationMs); } catch (InterruptedException ignored) {}
            channel.noteOff(midi);
        }).start();
    }

    public void playPreviewNotes(List<Integer> midiNotes, int durationMs) {
        if (channel == null || midiNotes == null || midiNotes.isEmpty()) return;
        for (int m : midiNotes) channel.noteOn(m, 90);
        new Thread(() -> {
            try { Thread.sleep(durationMs); } catch (InterruptedException ignored) {}
            for (int m : midiNotes) channel.noteOff(m);
        }).start();
    }

    public void playMetronomeTick(boolean accent) {
        MidiChannel pc = (percussionChannel != null) ? percussionChannel : channel;
        if (pc == null) return;
        int tickNote = accent ? 76 : 37;
        int vel = accent ? 120 : 90;
        pc.noteOn(tickNote, vel);
        new Thread(() -> {
            try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            pc.noteOff(tickNote);
        }).start();
    }

    public int noteNameToMidi(String name) {
        if (name == null || name.length() < 2) return -1;
        name = name.trim();
        int idx = 0;
        char base = Character.toUpperCase(name.charAt(idx));
        int semitone;
        switch (base) {
            case 'C': semitone = 0; break;
            case 'D': semitone = 2; break;
            case 'E': semitone = 4; break;
            case 'F': semitone = 5; break;
            case 'G': semitone = 7; break;
            case 'A': semitone = 9; break;
            case 'B': semitone = 11; break;
            default: return -1;
        }
        idx++;
        if (idx < name.length()) {
            char acc = name.charAt(idx);
            if (acc == '#' || acc == '♯') { semitone += 1; idx++; }
            else if (acc == 'b' || acc == '♭') { semitone -= 1; idx++; }
        }
        if (idx >= name.length()) return -1;
        int octave;
        try { octave = Integer.parseInt(name.substring(idx)); } catch (NumberFormatException ex) { return -1; }
        int midi = (octave + 1) * 12 + semitone;
        return midi;
    }
}
