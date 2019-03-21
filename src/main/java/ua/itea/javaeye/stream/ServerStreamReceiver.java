package ua.itea.javaeye.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import ua.itea.javaeye.utils.JavaEyeUtils;

public class ServerStreamReceiver implements Runnable {

	private AudioInputStream ais;
	private AudioFormat format;
	private volatile boolean status;
	private int port = JavaEyeUtils.SOUND_PORT;
	private int sampleRate = JavaEyeUtils.SAMPLE_RATE;
	private int channels = JavaEyeUtils.CHANNELS;
	private int sampleSize = JavaEyeUtils.SAMPLE_SIZE;

	private DataLine.Info dataLineInfo;
	private SourceDataLine sourceDataLine;

	private DatagramSocket serverSocket = null;

	public ServerStreamReceiver() {
		this.status = true;
	}

	public void setStatus(boolean status) {
		this.status = status;
	}

	public void toSpeaker(byte soundbytes[]) {
		try {
			sourceDataLine.write(soundbytes, 0, soundbytes.length);
		} catch (Exception e) {
			System.out.println("Not working in speakers...");
			e.printStackTrace();
		}
	}

	public void stop() {
		this.status = false;
		serverSocket.close();
		sourceDataLine.drain();
		sourceDataLine.close();

		System.out.println("Sound receiver stopped");
	}

	@Override
	public void run() {
		System.out.println("Sound receiver listening port: " + port);
		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			System.out.println("Closing socket");
			// e.printStackTrace();
		}

		byte[] receiveData = new byte[4096];

		format = new AudioFormat(sampleRate, sampleSize, channels, true, false);
		dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
		try {
			sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
			sourceDataLine.open(format);
		} catch (LineUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		sourceDataLine.start();

		// FloatControl volumeControl = (FloatControl)
		// sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
		// volumeControl.setValue(1.00f);

		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		ByteArrayInputStream bais = new ByteArrayInputStream(receivePacket.getData());

		while (true) {
			if (status == false) {
				break;
			} else {
				try {
					serverSocket.receive(receivePacket);
				} catch (IOException e) {
					System.out.println("Sound receiver socket closed");
					// e.printStackTrace();
				}
				ais = new AudioInputStream(bais, format, receivePacket.getLength());
				toSpeaker(receivePacket.getData());
			}
		}
	}
}