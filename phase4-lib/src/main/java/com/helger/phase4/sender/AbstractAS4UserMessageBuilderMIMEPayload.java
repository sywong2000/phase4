/*
 * Copyright (C) 2015-2023 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.sender;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.annotation.OverrideOnDemand;
import com.helger.phase4.attachment.AS4OutgoingAttachment;
import com.helger.phase4.attachment.WSS4JAttachment;
import com.helger.phase4.client.AS4ClientUserMessage;
import com.helger.phase4.crypto.AS4IncomingSecurityConfiguration;
import com.helger.phase4.util.AS4ResourceHelper;
import com.helger.phase4.util.Phase4Exception;

/**
 * Abstract builder base class for a user messages that put the payload in a
 * MIME part.
 *
 * @author Philip Helger
 * @param <IMPLTYPE>
 *        The implementation type
 * @since 0.10.0
 */
@NotThreadSafe
public abstract class AbstractAS4UserMessageBuilderMIMEPayload <IMPLTYPE extends AbstractAS4UserMessageBuilderMIMEPayload <IMPLTYPE>>
                                                               extends
                                                               AbstractAS4UserMessageBuilder <IMPLTYPE>
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractAS4UserMessageBuilderMIMEPayload.class);

  private AS4OutgoingAttachment m_aPayload;

  /**
   * Create a new builder, with the some fields already set as outlined in
   * {@link AbstractAS4UserMessageBuilder#AbstractAS4UserMessageBuilder()}
   */
  protected AbstractAS4UserMessageBuilderMIMEPayload ()
  {}

  /**
   * Set the payload to be send out.
   *
   * @param aBuilder
   *        The payload builder to be used. May be <code>null</code>.
   * @return this for chaining
   */
  @Nonnull
  public final IMPLTYPE payload (@Nullable final AS4OutgoingAttachment.Builder aBuilder)
  {
    return payload (aBuilder == null ? null : aBuilder.build ());
  }

  /**
   * Set the payload to be send out.
   *
   * @param aPayload
   *        The payload to be used. May be <code>null</code>.
   * @return this for chaining
   */
  @Nonnull
  public final IMPLTYPE payload (@Nullable final AS4OutgoingAttachment aPayload)
  {
    m_aPayload = aPayload;
    return thisAsT ();
  }

  @Override
  @OverridingMethodsMustInvokeSuper
  public boolean isEveryRequiredFieldSet ()
  {
    if (!super.isEveryRequiredFieldSet ())
      return false;

    if (m_aPayload == null)
    {
      LOGGER.warn ("The field 'payload' is not set");
      return false;
    }

    // All valid
    return true;
  }

  /**
   * Create the main attachment. This is mainly intended for ENTSOG to add some
   * custom properties.
   *
   * @param aPayload
   *        The outgoing main attachment as provided to the builder. Never
   *        <code>null</code>.s
   * @param aResHelper
   *        The resource helper to use for temporary files etc. Never
   *        <code>null</code>.
   * @return May be <code>null</code> to indicate not to use the main payload
   * @throws IOException
   *         in case of error
   */
  @Nullable
  @OverrideOnDemand
  protected WSS4JAttachment createMainAttachment (@Nonnull final AS4OutgoingAttachment aPayload,
                                                  @Nonnull final AS4ResourceHelper aResHelper) throws IOException
  {
    return WSS4JAttachment.createOutgoingFileAttachment (aPayload, aResHelper);
  }

  @Override
  protected final void mainSendMessage () throws Phase4Exception
  {
    // Temporary file manager
    try (final AS4ResourceHelper aResHelper = new AS4ResourceHelper ())
    {
      // Start building AS4 User Message
      final AS4ClientUserMessage aUserMsg = new AS4ClientUserMessage (aResHelper);
      applyToUserMessage (aUserMsg);

      if (m_aSendingDTConsumer != null)
      {
        try
        {
          // Eventually this call will determine the sendingDateTime if none is
          // set yet
          m_aSendingDTConsumer.onEffectiveSendingDateTime (aUserMsg.ensureSendingDateTime ().getSendingDateTime ());
        }
        catch (final Exception ex)
        {
          LOGGER.error ("Failed to invoke IAS4SendingDateTimeConsumer", ex);
        }
      }

      // No payload - only one attachment
      aUserMsg.setPayload (null);

      // Add main attachment
      {
        final WSS4JAttachment aMainAttachment = WSS4JAttachment.createOutgoingFileAttachment (m_aPayload, aResHelper);
        if (aMainAttachment != null)
          aUserMsg.addAttachment (aMainAttachment);
      }

      // Add other attachments
      for (final AS4OutgoingAttachment aAttachment : m_aAttachments)
        aUserMsg.addAttachment (WSS4JAttachment.createOutgoingFileAttachment (aAttachment, aResHelper));

      // Create on demand with all necessary parameters
      final AS4IncomingSecurityConfiguration aIncomingSecurityConfiguration = new AS4IncomingSecurityConfiguration ().setSecurityProviderSign (m_aSigningParams.getSecurityProvider ())
                                                                                                                     .setSecurityProviderCrypt (m_aCryptParams.getSecurityProvider ())
                                                                                                                     .setDecryptParameterModifier (m_aDecryptParameterModifier);

      // Main sending
      AS4BidirectionalClientHelper.sendAS4UserMessageAndReceiveAS4SignalMessage (m_aCryptoFactorySign,
                                                                                 m_aCryptoFactoryCrypt,
                                                                                 pmodeResolver (),
                                                                                 incomingAttachmentFactory (),
                                                                                 incomingProfileSelector (),
                                                                                 aUserMsg,
                                                                                 m_aLocale,
                                                                                 m_sEndpointURL,
                                                                                 m_aBuildMessageCallback,
                                                                                 m_aOutgoingDumper,
                                                                                 m_aIncomingDumper,
                                                                                 aIncomingSecurityConfiguration,
                                                                                 m_aRetryCallback,
                                                                                 m_aResponseConsumer,
                                                                                 m_aSignalMsgConsumer);
    }
    catch (final Phase4Exception ex)
    {
      // Re-throw
      throw ex;
    }
    catch (final Exception ex)
    {
      // TODO If this is a ExtendedHttpResponseException than the incoming
      // dumper is never invoked
      // Wrap in phase4 Exception
      throw new Phase4Exception ("Wrapped Phase4Exception", ex);
    }
  }
}
