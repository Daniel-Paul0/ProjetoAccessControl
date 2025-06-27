package com.project;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

public class CAAplett extends Applet {

	// code of CLA byte in the command APDU header
    final static byte CONTROLLA_CLA = (byte) 0x80;
	
	// codes of INS byte in the command APDU header
    private static final byte INS_SET_ID = (byte) 0x01;
    private static final byte INS_GET_ID = (byte) 0x02;
    private static final byte INS_SET_AES_KEY = (byte) 0x03;
    private static final byte INS_GET_DIGEST = (byte) 0x04;
    private static final byte INS_GET_ENCRYPTED_ID = (byte) 0x06;
    private static final byte INS_VERIFY_PIN = (byte) 0x07;
    private static final byte INS_SET_PIN = (byte) 0x08;
    private static final byte INS_GET_ENCRYPTED_CHALLENGE = (byte) 0x09;
    
    // signal that the PIN verification failed
    final static short SW_VERIFICATION_FAILED = 0x6300;
    
    // maximum size ID
    private static final short MAX_ID_LENGTH = 64;
    
    // maximum size PIN
    final static byte MAX_PIN_SIZE = 0x06;
    final static byte PIN_TRY_LIMIT = 0x03;
    
    // Card PIN
    OwnerPIN pin;
   
    // symmetric key
    private AESKey symmetricKey;
    // card ID
    private byte[] cardId;
    // card ID length
    private short idLength;
 
    // hash function
     private MessageDigest hashFunction;
        
    // cipher
    private Cipher aesCipher;
    
    private RandomData random;
    
    // buffers to create iv
    private byte[] ivBuffer;
    private byte[] aesInputBuffer; 
    private byte[] aesInputBufferD;
    
    // buffer for modulus
    private byte[] digestBuffer;
    
    
	protected CAAplett() {
        cardId = new byte[MAX_ID_LENGTH];
        idLength = 0;
        
        // initialize pin 
        pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);
        
        // builds space to get the key
        symmetricKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_256, false);
        
        // instance the cipher AES 
        aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        
        // creates hash function
        hashFunction = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
              
        // initialize ivs buffers for not save the signature after use
        ivBuffer = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);
        aesInputBuffer = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
        aesInputBufferD = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
        digestBuffer = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
        
        random = RandomData.getInstance(RandomData.ALG_PSEUDO_RANDOM);
        
        
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CAAplett();
    }
    
    @Override
    public void deselect() {
        // clean temporary buffer after deselected
        Util.arrayFillNonAtomic(ivBuffer, (short) 0, (short) ivBuffer.length, (byte) 0x00);
        Util.arrayFillNonAtomic(aesInputBuffer, (short) 0, (short) aesInputBuffer.length, (byte) 0x00);
        Util.arrayFillNonAtomic(digestBuffer, (short) 0, (short) digestBuffer.length, (byte) 0x00);
        Util.arrayFillNonAtomic(aesInputBufferD, (short) 0, (short) aesInputBuffer.length, (byte) 0x00);
    }
    
    @Override
    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (selectingApplet()) {
            return;
        }
        
        // verify the reset of commands have the
        // correct CLA byte, which specifies the
        // command structure
        
        if(buffer[ISO7816.OFFSET_CLA] != CONTROLLA_CLA) {
        	ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_SET_ID:
                setId(apdu);
                break;
            case INS_GET_ID:
                getId(apdu);
                break;
            case INS_SET_AES_KEY:
            	setAESKEY(apdu);
            	break;
            case INS_GET_DIGEST:
            	getDigest(apdu);
            	break;
            case INS_GET_ENCRYPTED_ID:
            	getEncryptedId(apdu);
                break;
            case INS_VERIFY_PIN:
            	verifyPIN(apdu);
                break;
            case INS_SET_PIN:
            	setPIN(apdu);
                break; 
            case INS_GET_ENCRYPTED_CHALLENGE:
            	getEncryptedChallenge(apdu);
            	break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                break;
        }
    }

    private void setId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if (len > MAX_ID_LENGTH) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, cardId, (short) 0, len);
        idLength = len;
    }

    private void getId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (idLength == 0) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        Util.arrayCopyNonAtomic(cardId, (short) 0, buffer, (short) 0, idLength);
        apdu.setOutgoingAndSend((short) 0, idLength);
    }
    
    private void setAESKEY(APDU apdu) {
    	byte[] buffer = apdu.getBuffer();
    	short len = apdu.setIncomingAndReceive();
    	
    	if (len != 32) {
    		ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    	}
    	
    	// copy the received key
    	symmetricKey.setKey(buffer, ISO7816.OFFSET_CDATA);
    }
    
    private void getDigest(APDU apdu) {
    	byte[] buffer = apdu.getBuffer();
    	short len = apdu.setIncomingAndReceive();
    	
    	if(len <= 0 || len > 32) {
    		ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    	}     	
    	
    	hashFunction.reset();
    	short digestLen = hashFunction.doFinal(buffer, ISO7816.OFFSET_CDATA, len, buffer, (short) 0);
    	
    	apdu.setOutgoingAndSend((short) 0, digestLen);	
    }
    
    @SuppressWarnings("deprecation")
	private void getEncryptedId(APDU apdu){
    	byte[] buffer = apdu.getBuffer();
    	
        // generate a random iv
        random.generateData(ivBuffer, (short) 0, (short) 16);

        // fill buffer AES with zero padding 
        short paddedLen = (short) (((idLength + 15) / 16) * 16);
        Util.arrayFillNonAtomic(aesInputBuffer, (short) 0, paddedLen, (byte) 0x00);
        Util.arrayCopyNonAtomic(cardId, (short) 0, aesInputBuffer, (short) 0, idLength);

        // Initialize AES Cipher
        aesCipher.init(symmetricKey, Cipher.MODE_ENCRYPT, ivBuffer, (short) 0, (short) 16);

        short encLen = aesCipher.doFinal(aesInputBuffer, (short) 0, paddedLen, buffer, (short) 16);

        // add the iv to the beginning of the response buffer
        Util.arrayCopyNonAtomic(ivBuffer, (short) 0, buffer, (short) 0, (short) 16);

        apdu.setOutgoing();
        apdu.setOutgoingLength((short) (16 + encLen));
        apdu.sendBytes((short) 0, (short) (16 + encLen));
    }
    
    private void verifyPIN(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        
        // retrieve the PIN data for validation.
        byte byteRead = (byte) apdu.setIncomingAndReceive();

        // check pin
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead) == false) {
            ISOException.throwIt(SW_VERIFICATION_FAILED);
        }
    } 
    
    private void setPIN(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if (len > MAX_PIN_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        pin.update(buffer, ISO7816.OFFSET_CDATA, (byte) len);
    }
    
    private void getEncryptedChallenge(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        if(len <= 0 || len > 64) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // extract iv first 16 bytes
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, ivBuffer, (short) 0, (short) 16);

        // Initialize AES Cipher
        aesCipher.init(symmetricKey, Cipher.MODE_DECRYPT, ivBuffer, (short) 0, (short) 16);
        short decryptedLen = aesCipher.doFinal(buffer, (short)(ISO7816.OFFSET_CDATA + 16), (short)(len - 16), aesInputBufferD, (short) 0);

        // Do Digest
        hashFunction.reset();
        short digestLen = hashFunction.doFinal(aesInputBufferD, (short) 0, decryptedLen, digestBuffer, (short) 0); 

        // generate a random iv
        random.generateData(ivBuffer, (short) 0, (short) 16);

        // fill buffer AES with zero padding 
        short paddedLen = (short) (((digestLen + 15) / 16) * 16);
        Util.arrayFillNonAtomic(aesInputBuffer, (short) 0, paddedLen, (byte) 0x00);
        Util.arrayCopyNonAtomic(digestBuffer, (short) 0, aesInputBuffer, (short) 0, digestLen);

        // Initialize AES Cipher
        aesCipher.init(symmetricKey, Cipher.MODE_ENCRYPT, ivBuffer, (short) 0, (short) 16);
        short encLen = aesCipher.doFinal(aesInputBuffer, (short) 0, paddedLen, buffer, (short) 16);

        // add the iv to the beginning of the response buffer
        Util.arrayCopyNonAtomic(ivBuffer, (short) 0, buffer, (short) 0, (short) 16);

        apdu.setOutgoing();
        apdu.setOutgoingLength((short) (16 + encLen));
        apdu.sendBytes((short) 0, (short) (16 + encLen));
    }

}
