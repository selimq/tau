package com.dooboolab.fluttersound;
/*
 * Copyright 2018, 2019, 2020 Dooboolab.
 *
 * This file is part of Flutter-Sound.
 *
 * Flutter-Sound is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3, as published by
 * the Free Software Foundation.
 *
 * Flutter-Sound is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Flutter-Sound.  If not, see <https://www.gnu.org/licenses/>.
 */



import android.content.Context;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import android.media.AudioFocusRequest;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

class FlautoPlayerPlugin
	implements MethodCallHandler
{
	final static String TAG = "FlutterPlayerPlugin";
	public static MethodChannel      channel;
	public static List<FlutterSoundPlayer> slots;
	static        Context            androidContext;
	static        FlautoPlayerPlugin flautoPlayerPlugin; // singleton


	public static void attachFlautoPlayer (
		Context ctx, BinaryMessenger messenger
	                                      )
	{
		assert ( flautoPlayerPlugin == null );
		flautoPlayerPlugin = new FlautoPlayerPlugin ();
		assert ( slots == null );
		slots   = new ArrayList<FlutterSoundPlayer> ();
		channel = new MethodChannel ( messenger, "com.dooboolab.flutter_sound_player" );
		channel.setMethodCallHandler ( flautoPlayerPlugin );
		androidContext = ctx;

	}


	void invokeMethod ( String methodName, Map dic )
	{
		//Log.d(TAG, "FlutterAutoPlugin: invokeMethod" + methodName);
		channel.invokeMethod ( methodName, dic );
	}

	void freeSlot ( int slotNo )
	{
		slots.set ( slotNo, null );
	}



	FlautoPlayerPlugin getManager ()
	{
		return flautoPlayerPlugin;
	}

	@Override
	public void onMethodCall (
		final MethodCall call, final Result result
	                         )
	{
		int slotNo = call.argument ( "slotNo" );
		assert ( ( slotNo >= 0 ) && ( slotNo <= slots.size () ) );

		if ( slotNo == slots.size () )
		{
			slots.add ( slotNo, null );
		}

		FlutterSoundPlayer aPlayer = slots.get ( slotNo );
		switch ( call.method )
		{

			case "initializeMediaPlayer":
			{
				assert ( slots.get ( slotNo ) == null );
				aPlayer = new FlutterSoundPlayer ( slotNo );
				slots.set ( slotNo, aPlayer );
				aPlayer.initializeFlautoPlayer ( call, result );

			}
			break;

			case "releaseMediaPlayer":
			{
				aPlayer.releaseFlautoPlayer ( call, result );
				slots.set ( slotNo, null );
			}
			break;

			case "initializeMediaPlayerWithUI":
			{
				assert ( slots.get ( slotNo ) == null );
				aPlayer = new TrackPlayer ( slotNo );
				slots.set ( slotNo, aPlayer );
				aPlayer.initializeFlautoPlayer ( call, result );
			}
			break;

			case "setFocus":
			{
				aPlayer.setFocus ( call, result );
			}
			break;


			case "isDecoderSupported":
			{
				aPlayer.isDecoderSupported ( call, result );
			}
			break;

			case "startPlayer":
			{
				aPlayer.startPlayer ( call, result );
			}
			break;

			case "startPlayerFromTrack":
			{
				aPlayer.startPlayerFromTrack ( call, result );
			}
			break;


			case "stopPlayer":
			{
				aPlayer.stopPlayer ( call, result );
			}
			break;

			case "pausePlayer":
			{
				aPlayer.pausePlayer ( call, result );
			}
			break;

			case "resumePlayer":
			{
				aPlayer.resumePlayer ( call, result );
			}
			break;

			case "seekToPlayer":
			{
				aPlayer.seekToPlayer ( call, result );
			}
			break;

			case "setVolume":
			{
				aPlayer.setVolume ( call, result );
			}
			break;

			case "setSubscriptionDuration":
			{
				aPlayer.setSubscriptionDuration ( call, result );
			}
			break;

			case "androidAudioFocusRequest":
			{
				aPlayer.androidAudioFocusRequest ( call, result );
			}
			break;

			case "setActive":
			{
				aPlayer.setActive ( call, result );
			}
			break;

			default:
			{
				result.notImplemented ();
			}
			break;
		}
	}

}


// SDK compatibility
// -----------------

class sdkCompat
{
	static final int AUDIO_ENCODER_VORBIS = 6; // MediaRecorder.AudioEncoder.VORBIS added in API level 21
	static final int AUDIO_ENCODER_OPUS   = 7; // MediaRecorder.AudioEncoder.OPUS added in API level 29
	static final int OUTPUT_FORMAT_OGG    = 11; // MediaRecorder.OutputFormat.OGG added in API level 29
	static final int VERSION_CODES_M      = 23; // added in API level 23
	static final int ENCODING_PCM_16BIT   = 2;
	static final int ENCODING_OPUS        = 20; // Android R
}

class PlayerAudioModel
{
	final public static String DEFAULT_FILE_LOCATION = Environment.getDataDirectory ().getPath () + "/default.aac"; // SDK
	public              int    subsDurationMillis    = 10;

	private MediaPlayer mediaPlayer;
	private long        playTime = 0;


	public MediaPlayer getMediaPlayer ()
	{
		return mediaPlayer;
	}

	public void setMediaPlayer ( MediaPlayer mediaPlayer )
	{
		this.mediaPlayer = mediaPlayer;
	}

	public long getPlayTime ()
	{
		return playTime;
	}

	public void setPlayTime ( long playTime )
	{
		this.playTime = playTime;
	}
}

//-------------------------------------------------------------------------------------------------------------


public class FlutterSoundPlayer implements MediaPlayer.OnErrorListener
{

/// to control the focus mode.
	enum AudioFocus {
		requestFocus,

		/// request focus and allow other audio
		/// to continue playing at their current volume.
		requestFocusAndKeepOthers,

		/// request focus and stop other audio playing
		requestFocusAndStopOthers,

		/// request focus and reduce the volume of other players
		/// In the Android world this is know as 'Duck Others'.
		requestFocusAndDuckOthers,

		requestFocusAndInterruptSpokenAudioAndMixWithOthers,

		requestFocusTransient,
		requestFocusTransientExclusive,

		/// relinquish the audio focus.
		abandonFocus,

		doNotRequestFocus,
	}


	final static int CODEC_OPUS   = 2;
	final static int CODEC_VORBIS = 5;

	static boolean _isAndroidDecoderSupported[] = {
		true, // DEFAULT
		true, // aacADTS
		true, // opusOGG
		true, // opusCAF
		true, // MP3
		true, // vorbisOGG
		true, // pcm16 // Really ???
		true, // pcm16WAV
		false, // pcm16AIFF
		false, // pcm16CAF
		true, // flac
		true, // aacMP4
	};


	String extentionArray[] = {
		".aac" // DEFAULT
		, ".aac" // CODEC_AAC
		, ".opus" // CODEC_OPUS
		, "_opus.caf" // CODEC_CAF_OPUS (this is apple specific)
		, ".mp3" // CODEC_MP3
		, ".ogg" // CODEC_VORBIS
		, ".pcm" // CODEC_PCM
		, ".wav"
		, ".aiff"
		, "._pcm.caf"
		, ".flac"
		, ".mp4"
	};


	final static  String           TAG         = "FlutterSoundPlugin";
	final         PlayerAudioModel model       = new PlayerAudioModel ();
	private       Timer            mTimer      = new Timer ();
	final private Handler          mainHandler = new Handler ();
	AudioFocusRequest   audioFocusRequest = null;
	AudioManager        audioManager;
	int                 slotNo;
	boolean hasFocus = false;


	static final String ERR_UNKNOWN           = "ERR_UNKNOWN";
	static final String ERR_PLAYER_IS_NULL    = "ERR_PLAYER_IS_NULL";
	static final String ERR_PLAYER_IS_PLAYING = "ERR_PLAYER_IS_PLAYING";

	FlutterSoundPlayer ( int aSlotNo )
	{
		slotNo = aSlotNo;
	}


	FlautoPlayerPlugin getPlugin ()
	{
		return FlautoPlayerPlugin.flautoPlayerPlugin;
	}


	boolean prepareFocus( final MethodCall call)
	{
		boolean r = true;
		audioManager = ( AudioManager ) FlautoPlayerPlugin.androidContext.getSystemService ( Context.AUDIO_SERVICE );
		AudioFocus focus = AudioFocus.values()[(int)call.argument ( "focus" )];

		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
		{
			if ( focus != AudioFocus.abandonFocus && focus != AudioFocus.doNotRequestFocus && focus != AudioFocus.requestFocus )
			{
				int audioFlags = call.argument( "audioFlags" );
				int focusGain = AudioManager.AUDIOFOCUS_GAIN;

				switch (focus)
				{
					case requestFocusAndDuckOthers: focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK; ; break;
					case requestFocusAndKeepOthers: focusGain = AudioManager.AUDIOFOCUS_GAIN; ; break;
					case requestFocusTransient: focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT; break;
					case requestFocusTransientExclusive: focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE; break;
					case requestFocusAndInterruptSpokenAudioAndMixWithOthers: focusGain = AudioManager.AUDIOFOCUS_GAIN; break;
					case requestFocusAndStopOthers: focusGain = AudioManager.AUDIOFOCUS_GAIN; break;
				}
				audioFocusRequest = new AudioFocusRequest.Builder( focusGain )
					// .setAudioAttributes(mPlaybackAttributes)
					.build();

				/*
				if (flags & outputToSpeaker)
					sessionCategoryOption |= AVAudioSessionCategoryOptionDefaultToSpeaker;
				if (flags & allowAirPlay)
					sessionCategoryOption |= AVAudioSessionCategoryOptionAllowAirPlay;
				if (flags & allowBlueTooth)
					sessionCategoryOption |= AVAudioSessionCategoryOptionAllowBluetooth;
				if (flags & allowBlueToothA2DP)
					sessionCategoryOption |= AVAudioSessionCategoryOptionAllowBluetoothA2DP;
				 */
			}

			if (focus != AudioFocus.doNotRequestFocus)
			{
				hasFocus = (focus != AudioFocus.abandonFocus);
				//r = [[AVAudioSession sharedInstance]  setActive: hasFocus error:nil] ;
				if (hasFocus)
					audioManager.requestAudioFocus ( audioFocusRequest );
				else
					audioManager.abandonAudioFocusRequest ( audioFocusRequest );
			}
		}
		return r;
	}

	void setFocus ( final MethodCall call, final Result result )
	{
		boolean r = prepareFocus(call);

	}

	void initializeFlautoPlayer ( final MethodCall call, final Result result )
	{
		audioManager = ( AudioManager ) FlautoPlayerPlugin.androidContext.getSystemService ( Context.AUDIO_SERVICE );
		boolean r = prepareFocus(call);
		if (r)
		        result.success ( r);
		else
			result.error ( ERR_UNKNOWN, ERR_UNKNOWN, "Failure to open session");

	}

		void releaseFlautoPlayer ( final MethodCall call, final Result result )
	{
		if (hasFocus)
			abandonFocus();
		result.success ( "Flauto Recorder Released" );
	}



	void invokeMethodWithString ( String methodName, String arg )
	{
		Map<String, Object> dic = new HashMap<String, Object> ();
		dic.put ( "slotNo", slotNo );
		dic.put ( "arg", arg );
		getPlugin ().invokeMethod ( methodName, dic );
	}

	void invokeMethodWithDouble ( String methodName, double arg )
	{
		Map<String, Object> dic = new HashMap<String, Object> ();
		dic.put ( "slotNo", slotNo );
		dic.put ( "arg", arg );
		getPlugin ().invokeMethod ( methodName, dic );
	}

	void invokeMethodWithMap ( String methodName, Map<String, Object>  dic )
	{
		dic.put ( "slotNo", slotNo );
		getPlugin ().invokeMethod ( methodName, dic );
	}



	public void startPlayer ( final MethodCall call, final Result result )
	{
		Integer           _codec     = call.argument ( "codec" );
		FlutterSoundCodec codec      = FlutterSoundCodec.values()[ ( _codec != null ) ? _codec : 0 ];
		byte[]            dataBuffer = call.argument ( "fromDataBuffer" );
		String path = call.argument("fromURI");
		//if ( ! hasFocus ) // We always require focus because it could have been abandoned by another Session
		{
			requestFocus ();
		}
		if (dataBuffer != null)
		{
			try
			{
				File             f   = File.createTempFile ( "flutter_sound_buffer-" + Integer.toString(slotNo), extentionArray[ codec.ordinal () ] );
				FileOutputStream fos = new FileOutputStream ( f );
				fos.write ( dataBuffer );
				path = f.getPath();
			}
			catch ( Exception e )
			{
				result.error ( ERR_UNKNOWN, ERR_UNKNOWN, e.getMessage () );
				return;
			}

		}
		if ( this.model.getMediaPlayer () != null )
		{
			Boolean isPaused = !this.model.getMediaPlayer ().isPlaying () && this.model.getMediaPlayer ().getCurrentPosition () > 1;

			if ( isPaused )
			{
				this.model.getMediaPlayer ().start ();
				result.success ( true );
				return;
			}

			Log.e ( TAG, "Player is already running. Stop it first." );
			result.success ( false );
			return;
		} else
		{
			this.model.setMediaPlayer ( new MediaPlayer () );
		}
		mTimer = new Timer ();

		try
		{
			if ( path == null )
			{
				this.model.getMediaPlayer ().setDataSource ( PlayerAudioModel.DEFAULT_FILE_LOCATION );
			}
			else
			{
				this.model.getMediaPlayer ().setDataSource (  path );
			}
			final String pathFile = path;
			this.model.getMediaPlayer ().setOnPreparedListener ( mp -> onPrepared(mp, pathFile)	);
			/*
			 * Detect when finish playing.
			 */
			this.model.getMediaPlayer ().setOnCompletionListener ( mp -> onCompletion(mp)
			                                                      );
			this.model.getMediaPlayer ().setOnErrorListener(this);

			this.model.getMediaPlayer ().prepare ();
		}
		catch ( Exception e )
		{
			Log.e ( TAG, "startPlayer() exception" );
			result.error ( ERR_UNKNOWN, ERR_UNKNOWN, e.getMessage () );
			return;
		}
		result.success ( true );
	}

	public void startPlayerFromTrack ( final MethodCall call, final Result result )
	{
		result.error ( ERR_UNKNOWN, ERR_UNKNOWN, "Must be initialized With UI" );
	}

	public boolean onError(MediaPlayer mp, int what, int extra) {
	// ... react appropriately ...
	// The MediaPlayer has moved to the Error state, must be reset!
	return false;
	}


	// listener called when media player has completed playing.
	private void onCompletion(MediaPlayer mp)
	{
			/*
			 * Reset player.
			 */
			Log.d(TAG, "Playback completed.");
				invokeMethodWithString("audioPlayerFinishedPlaying", null);
			mTimer.cancel();
			if (mp.isPlaying()) {
				mp.stop();
			}
			mp.reset();
			mp.release();
			model.setMediaPlayer(null);
	}

	// Listener called when media player has completed preparation.
	private void onPrepared(MediaPlayer mp, String path)
	{
		Log.d(TAG, "mediaPlayer prepared and start");
		mp.start();

		/*
		 * Set timer task to send event to RN.
		 */
		TimerTask mTask = new TimerTask() {
			@Override
			public void run() {
				try {
					long position = mp.getCurrentPosition();
					long duration = mp.getDuration();
					Map<String, Object> dic = new HashMap<String, Object> ();
					dic.put ( "position", position );
					dic.put ( "duration", duration );

					mainHandler.post(new Runnable() {
						@Override
						public void run() {
							invokeMethodWithMap("updateProgress", dic);
						}
					});


				} catch (Exception e) {
					Log.d(TAG, "Exception: " + e.toString());
				}
			}
		};

		mTimer.schedule(mTask, 0, model.subsDurationMillis);
		String resolvedPath = (path == null) ? PlayerAudioModel.DEFAULT_FILE_LOCATION : path;
		//result.success((resolvedPath));
	}


	public void stopPlayer ( final MethodCall call, final Result result )
	{
		mTimer.cancel ();

		if ( this.model.getMediaPlayer () == null )
		{
			result.success ( "Player already Closed");
			return;
		}

		try
		{
			this.model.getMediaPlayer ().stop ();
			this.model.getMediaPlayer ().reset ();
			this.model.getMediaPlayer ().release ();
			this.model.setMediaPlayer ( null );
			result.success ( "stopped player." );
		}
		catch ( Exception e )
		{
			Log.e ( TAG, "stopPlay exception: " + e.getMessage () );
			result.error ( ERR_UNKNOWN, ERR_UNKNOWN, e.getMessage () );
		}
	}

	public void isDecoderSupported ( final MethodCall call, final Result result )
	{
		int     _codec = call.argument ( "codec" );
		boolean b      = _isAndroidDecoderSupported[ _codec ];
		if ( Build.VERSION.SDK_INT < 23 )
		{
			if ( ( _codec == CODEC_OPUS ) || ( _codec == CODEC_VORBIS ) )
			{
				b = false;
			}
		}
		result.success ( b );

	}

	public void pausePlayer ( final MethodCall call, final Result result )
	{
		if ( this.model.getMediaPlayer () == null )
		{
			result.error ( ERR_PLAYER_IS_NULL, "pausePlayer()", ERR_PLAYER_IS_NULL );
			return;
		}
		try
		{
			this.model.getMediaPlayer ().pause ();
			result.success ( "paused player." );
		}
		catch ( Exception e )
		{
			Log.e ( TAG, "pausePlay exception: " + e.getMessage () );
			result.error ( ERR_UNKNOWN, ERR_UNKNOWN, e.getMessage () );
		}

	}

	public void resumePlayer ( final MethodCall call, final Result result )
	{
		if ( this.model.getMediaPlayer () == null )
		{
			result.error ( ERR_PLAYER_IS_NULL, "resumePlayer", ERR_PLAYER_IS_NULL );
			return;
		}

		if ( this.model.getMediaPlayer ().isPlaying () )
		{
			result.error ( ERR_PLAYER_IS_PLAYING, ERR_PLAYER_IS_PLAYING, ERR_PLAYER_IS_PLAYING );
			return;
		}

		try
		{
			this.model.getMediaPlayer ().seekTo ( this.model.getMediaPlayer ().getCurrentPosition () );
			this.model.getMediaPlayer ().start ();
			result.success ( "resumed player." );
		}
		catch ( Exception e )
		{
			Log.e ( TAG, "mediaPlayer resume: " + e.getMessage () );
			result.error ( ERR_UNKNOWN, ERR_UNKNOWN, e.getMessage () );
		}
	}

	public void seekToPlayer ( final MethodCall call, final Result result )
	{
		int millis = call.argument ( "sec" ) ;

		if ( this.model.getMediaPlayer () == null )
		{
			result.error ( ERR_PLAYER_IS_NULL, "seekToPlayer()", ERR_PLAYER_IS_NULL );
			return;
		}

		int currentMillis = this.model.getMediaPlayer ().getCurrentPosition ();
		Log.d ( TAG, "currentMillis: " + currentMillis );
		// millis += currentMillis; [This was the problem for me]

		Log.d ( TAG, "seekTo: " + millis );

		this.model.getMediaPlayer ().seekTo ( millis );
		result.success ( String.valueOf ( millis ) );
	}

	public void setVolume ( final MethodCall call, final Result result )
	{
		double volume = call.argument ( "volume" );

		if ( this.model.getMediaPlayer () == null )
		{
			result.error ( ERR_PLAYER_IS_NULL, "setVolume()", ERR_PLAYER_IS_NULL );
			return;
		}

		float mVolume = ( float ) volume;
		this.model.getMediaPlayer ().setVolume ( mVolume, mVolume );
		result.success ( "Set volume" );
	}


	public void setSubscriptionDuration ( final MethodCall call, Result result )
	{
		if ( call.argument ( "milliSec" ) == null )
		{
			return;
		}
		int duration = call.argument ( "milliSec" );

		this.model.subsDurationMillis = duration;
		result.success ( "setSubscriptionDuration: " + this.model.subsDurationMillis );
	}

	void androidAudioFocusRequest ( final MethodCall call, final Result result )
	{
		Integer focusGain = call.argument ( "focusGain" );

		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
		{
			audioFocusRequest = new AudioFocusRequest.Builder ( focusGain )
				// .setAudioAttributes(mPlaybackAttributes)
				// .setAcceptsDelayedFocusGain(true)
				// .setWillPauseWhenDucked(true)
				// .setOnAudioFocusChangeListener(this, mMyHandler)
				.build ();
			Boolean b = true;

			result.success ( b );
		} else
		{
			Boolean b = false;
			result.success ( b );
		}
	}

	boolean requestFocus ()
	{
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
		{
			if ( audioFocusRequest == null )
			{
				audioFocusRequest = new AudioFocusRequest.Builder ( AudioManager.AUDIOFOCUS_GAIN )
					//.setAudioAttributes(mPlaybackAttributes)
					//.setAcceptsDelayedFocusGain(true)
					//.setWillPauseWhenDucked(true)
					//.setOnAudioFocusChangeListener(this, mMyHandler)
					.build ();
			}
			hasFocus = true;
			return ( audioManager.requestAudioFocus ( audioFocusRequest ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED );
		} else
		{
			return false;
		}
	}

	boolean abandonFocus ()
	{
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
		{
			if ( audioFocusRequest == null )
			{
				audioFocusRequest = new AudioFocusRequest.Builder ( AudioManager.AUDIOFOCUS_GAIN )
					//.setAudioAttributes(mPlaybackAttributes)
					//.setAcceptsDelayedFocusGain(true)
					//.setWillPauseWhenDucked(true)
					//.setOnAudioFocusChangeListener(this, mMyHandler)
					.build ();
			}
			hasFocus = false;
			return ( audioManager.abandonAudioFocusRequest ( audioFocusRequest ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED );
		} else
		{
			return false;
		}

	}

	void setActive ( final MethodCall call, final Result result )
	{
		Boolean enabled = call.argument ( "enabled" );

		Boolean b = false;
		try
		{
			if ( enabled )
			{
				b  = requestFocus ();
			} else
			{

				b             = abandonFocus ();
			}
		}
		catch ( Exception e )
		{
			b = false;
		}
		result.success ( b );
	}


}

//-------------------------------------------------------------------------------------------------------------
