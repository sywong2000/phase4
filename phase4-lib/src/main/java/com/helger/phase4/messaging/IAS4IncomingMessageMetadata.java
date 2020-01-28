package com.helger.phase4.messaging;

import java.time.LocalDateTime;

import javax.annotation.CheckForSigned;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.string.StringHelper;

/**
 * This interface lets you access optional metadata for a single incoming
 * message.
 *
 * @author Philip Helger
 * @since 0.9.8
 */
public interface IAS4IncomingMessageMetadata
{
  /**
   * @return The date and time when the request was received. Never
   *         <code>null</code>.
   */
  @Nonnull
  LocalDateTime getIncomingDT ();

  /**
   * @return The message mode. May be <code>null</code>.
   */
  @Nonnull
  EAS4IncomingMessageMode getMode ();

  /**
   * Returns the Internet Protocol (IP) address of the client or last proxy that
   * sent the request.
   *
   * @return a <code>String</code> containing the IP address of the client that
   *         sent the request
   */
  @Nullable
  String getRemoteAddr ();

  default boolean hasRemoteAddr ()
  {
    return StringHelper.hasText (getRemoteAddr ());
  }

  /**
   * Returns the fully qualified name of the client or the last proxy that sent
   * the request. If the engine cannot or chooses not to resolve the hostname
   * (to improve performance), this method returns the dotted-string form of the
   * IP address.
   *
   * @return a <code>String</code> containing the fully qualified name of the
   *         client
   */
  @Nullable
  String getRemoteHost ();

  default boolean hasRemoteHost ()
  {
    return StringHelper.hasText (getRemoteHost ());
  }

  /**
   * Returns the Internet Protocol (IP) source port of the client or last proxy
   * that sent the request.
   *
   * @return an integer specifying the port number or a negative value if not
   *         set
   */
  @CheckForSigned
  int getRemotePort ();

  default boolean hasRemotePort ()
  {
    return getRemotePort () > 0;
  }
}
