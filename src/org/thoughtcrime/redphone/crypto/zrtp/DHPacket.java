/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.crypto.zrtp;

import org.thoughtcrime.redphone.network.RtpPacket;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Base DH packet, from which DH part one and DH part two derive.
 * http://tools.ietf.org/html/rfc6189#section-5.5
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DHPacket extends HandshakePacket {

  /**
   * We switch on these two KA types in several places below. This
   * is really unfortunate, particularly because we have distinct
   * sub-classes for the various KA types, so it stands to reason that
   * we should be able to isolate KA functionality to each KA type.
   *
   * Unfortunately, however, we already have sub-classes for PartOne
   * and PartTwo, which we really need.  So given the absence of multiple
   * inheritance, this is the only way to include the functionality without
   * duplicating code.  =(
   */

  protected static final int DH3K_AGREEMENT_TYPE = 1;
  protected static final int EC25_AGREEMENT_TYPE = 2;

  protected static final int DH3K_DH_LENGTH = 468;
  protected static final int EC25_DH_LENGTH = 148;

  private static final int LENGTH_OFFSET   = MESSAGE_BASE + 2;
  private static final int HASH_OFFSET     = MESSAGE_BASE + 12;
  private static final int RS1_OFFSET      = MESSAGE_BASE + 44;
  private static final int RS2_OFFSET      = MESSAGE_BASE + 52;
  private static final int AUX_OFFSET      = MESSAGE_BASE + 60;
  private static final int PBX_OFFSET      = MESSAGE_BASE + 68;
  private static final int PVR_OFFSET      = MESSAGE_BASE + 76;
  private static final int DH3K_MAC_OFFSET = PVR_OFFSET + 384;
  private static final int EC25_MAC_OFFSET = PVR_OFFSET + 64;

  private final int agreementType;

  public DHPacket(RtpPacket packet, int agreementType) {
    super(packet);
    this.agreementType = agreementType;
  }

  public DHPacket(RtpPacket packet, int agreementType, boolean deepCopy) {
    super(packet, deepCopy);
    this.agreementType = agreementType;
  }

  public DHPacket(String typeTag, int agreementType, HashChain hashChain, byte[] pvr) {
    super(typeTag, agreementType == DH3K_AGREEMENT_TYPE ? DH3K_DH_LENGTH : EC25_DH_LENGTH);

    setHash(hashChain.getH1());
    setState();
    setPvr(pvr);

    switch (agreementType) {
    case DH3K_AGREEMENT_TYPE:
      setMac(hashChain.getH0(), DH3K_MAC_OFFSET, DH3K_DH_LENGTH - 8);
      break;
    case EC25_AGREEMENT_TYPE:
      setMac(hashChain.getH0(), EC25_MAC_OFFSET, EC25_DH_LENGTH - 8);
      break;
    default:
      throw new AssertionError("Bad agreement type: " + agreementType);
    }

    this.agreementType = agreementType;
  }

  public byte[] getPvr() {
    switch (agreementType) {
    case DH3K_AGREEMENT_TYPE:
      byte[] dh3k_pvr = new byte[384];
      System.arraycopy(this.data, PVR_OFFSET, dh3k_pvr, 0, dh3k_pvr.length);
      return dh3k_pvr;
    case EC25_AGREEMENT_TYPE:
      byte[] ec25_pvr = new byte[64];
      System.arraycopy(this.data, PVR_OFFSET, ec25_pvr, 0, ec25_pvr.length);
      return ec25_pvr;
    default:
      throw new AssertionError("Bad agreement type: " + agreementType);
    }
  }

  public byte[] getHash() {
    byte[] hash = new byte[32];
    System.arraycopy(this.data, HASH_OFFSET, hash, 0, hash.length);
    return hash;
  }

  public void verifyMac(byte[] key) throws InvalidPacketException {
    switch (agreementType) {
    case DH3K_AGREEMENT_TYPE:
      super.verifyMac(key, DH3K_MAC_OFFSET, DH3K_DH_LENGTH-8, getHash());
      return;
    case EC25_AGREEMENT_TYPE:
      super.verifyMac(key, EC25_MAC_OFFSET, EC25_DH_LENGTH-8, getHash());
      return;
    default:
      throw new AssertionError("Bad agreement type: " + agreementType);
    }
  }

  private void setHash(byte[] hash) {
    System.arraycopy(hash, 0, this.data, HASH_OFFSET, hash.length);
  }

  private void setPvr(byte[] pvr) {
    System.arraycopy(pvr, 0, this.data, PVR_OFFSET, pvr.length);
  }

  private void setState() {
    try {
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      byte[] randomBytes  = new byte[8];

      for (int i=0;i<4;i++) {
        random.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, this.data, RS1_OFFSET + (i * 8), randomBytes.length);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
