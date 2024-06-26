/*
 * Copyright (C) 2024 Philip Helger (www.helger.com)
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
package com.helger.phase4.dbnalliance;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.state.ESuccess;
import com.helger.peppol.utils.PeppolCertificateHelper;
import com.helger.peppolid.IDocumentTypeIdentifier;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.peppolid.IProcessIdentifier;
import com.helger.phase4.CAS4;
import com.helger.phase4.crypto.ICryptoSessionKeyProvider;
import com.helger.phase4.dynamicdiscovery.AS4EndpointDetailProviderBDXR2;
import com.helger.phase4.dynamicdiscovery.AS4EndpointDetailProviderConstant;
import com.helger.phase4.dynamicdiscovery.IAS4EndpointDetailProvider;
import com.helger.phase4.profile.dbnalliance.DBNAlliancePMode;
import com.helger.phase4.sender.AbstractAS4UserMessageBuilderMIMEPayload;
import com.helger.phase4.sender.IAS4SendingDateTimeConsumer;
import com.helger.phase4.util.Phase4Exception;
import com.helger.smpclient.bdxr2.IBDXR2ServiceMetadataProvider;

/**
 * This class contains all the specifics to send AS4 messages with the
 * DBNAlliance profile. See <code>sendAS4Message</code> as the main method to
 * trigger the sending, with all potential customization.
 *
 * @author Philip Helger
 */
@Immutable
public final class Phase4DBNAllianceSender
{
  private static final Logger LOGGER = LoggerFactory.getLogger (Phase4DBNAllianceSender.class);

  private Phase4DBNAllianceSender ()
  {}

  /**
   * @return Create a new Builder for AS4 messages if the XHE payload is
   *         present. Never <code>null</code>.
   */
  @Nonnull
  public static DBNAllianceUserMessageBuilder builder ()
  {
    return new DBNAllianceUserMessageBuilder ();
  }

  /**
   * Abstract DBNAlliance UserMessage builder class with sanity methods.
   *
   * @author Philip Helger
   * @param <IMPLTYPE>
   *        The implementation type
   */
  public abstract static class AbstractDBNAllianceUserMessageBuilder <IMPLTYPE extends AbstractDBNAllianceUserMessageBuilder <IMPLTYPE>>
                                                                     extends
                                                                     AbstractAS4UserMessageBuilderMIMEPayload <IMPLTYPE>
  {
    // C4
    protected IParticipantIdentifier m_aReceiverID;
    protected IDocumentTypeIdentifier m_aDocTypeID;
    protected IProcessIdentifier m_aProcessID;

    protected IAS4EndpointDetailProvider m_aEndpointDetailProvider;
    private Consumer <X509Certificate> m_aCertificateConsumer;
    private Consumer <String> m_aAPEndpointURLConsumer;

    // Status var
    private OffsetDateTime m_aEffectiveSendingDT;

    protected AbstractDBNAllianceUserMessageBuilder ()
    {
      // Override default values
      try
      {
        httpClientFactory (new Phase4DBNAllianceHttpClientSettings ());
        agreementRef (DBNAlliancePMode.DEFAULT_AGREEMENT_ID);
        fromPartyIDType (DBNAlliancePMode.DEFAULT_PARTY_TYPE_ID);
        fromRole (CAS4.DEFAULT_INITIATOR_URL);
        toPartyIDType (DBNAlliancePMode.DEFAULT_PARTY_TYPE_ID);
        toRole (CAS4.DEFAULT_RESPONDER_URL);

        // Other crypt parameters are located in the PMode security part
        cryptParams ().setSessionKeyProvider (ICryptoSessionKeyProvider.INSTANCE_RANDOM_AES_256);
      }
      catch (final Exception ex)
      {
        throw new IllegalStateException ("Failed to init AS4 Client builder", ex);
      }
    }

    /**
     * Set the receiver participant ID of the message. The participant ID must
     * be provided prior to sending. This ends up in the "finalRecipient"
     * UserMessage property.
     *
     * @param aReceiverID
     *        The receiver participant ID. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE receiverParticipantID (@Nonnull final IParticipantIdentifier aReceiverID)
    {
      ValueEnforcer.notNull (aReceiverID, "ReceiverID");
      if (m_aReceiverID != null)
        LOGGER.warn ("An existing ReceiverParticipantID is overridden");
      m_aReceiverID = aReceiverID;
      return thisAsT ();
    }

    /**
     * @return The currently set Document Type ID. May be <code>null</code>.
     */
    @Nullable
    public final IDocumentTypeIdentifier documentTypeID ()
    {
      return m_aDocTypeID;
    }

    /**
     * Set the document type ID to be send. The document type must be provided
     * prior to sending. This is a shortcut to the {@link #action(String)}
     * method.
     *
     * @param aDocTypeID
     *        The document type ID to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE documentTypeID (@Nonnull final IDocumentTypeIdentifier aDocTypeID)
    {
      ValueEnforcer.notNull (aDocTypeID, "DocTypeID");
      if (m_aDocTypeID != null)
        LOGGER.warn ("An existing DocumentTypeID is overridden");
      m_aDocTypeID = aDocTypeID;
      return action (aDocTypeID.getURIEncoded ());
    }

    /**
     * @return The currently set Process ID. May be <code>null</code>.
     */
    @Nullable
    public final IProcessIdentifier processID ()
    {
      return m_aProcessID;
    }

    /**
     * Set the process ID to be send. The process ID must be provided prior to
     * sending. This is a shortcut to the {@link #service(String, String)}
     * method.
     *
     * @param aProcessID
     *        The process ID to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE processID (@Nonnull final IProcessIdentifier aProcessID)
    {
      ValueEnforcer.notNull (aProcessID, "ProcessID");
      if (m_aProcessID != null)
        LOGGER.warn ("An existing ProcessID is overridden");
      m_aProcessID = aProcessID;
      return service (aProcessID.getScheme (), aProcessID.getValue ());
    }

    /**
     * Set the abstract endpoint detail provider to be used. This can be an SMP
     * lookup routine or in certain test cases a predefined certificate and
     * endpoint URL.
     *
     * @param aEndpointDetailProvider
     *        The endpoint detail provider to be used. May not be
     *        <code>null</code>.
     * @return this for chaining
     * @see #smpClient(IBDXR2ServiceMetadataProvider)
     */
    @Nonnull
    public final IMPLTYPE endpointDetailProvider (@Nonnull final IAS4EndpointDetailProvider aEndpointDetailProvider)
    {
      ValueEnforcer.notNull (aEndpointDetailProvider, "EndpointDetailProvider");
      if (m_aEndpointDetailProvider != null)
        LOGGER.warn ("An existing EndpointDetailProvider is overridden");
      m_aEndpointDetailProvider = aEndpointDetailProvider;
      return thisAsT ();
    }

    /**
     * Set the SMP client to be used. This is the point where e.g. the
     * differentiation between SMK and SML can be done. This must be set prior
     * to sending. If the endpoint information are already known you can also
     * use {@link #receiverEndpointDetails(X509Certificate, String)} instead.
     *
     * @param aSMPClient
     *        The SMP client to be used. May not be <code>null</code>.
     * @return this for chaining
     * @see #receiverEndpointDetails(X509Certificate, String)
     * @see #endpointDetailProvider(IAS4EndpointDetailProvider)
     */
    @Nonnull
    public final IMPLTYPE smpClient (@Nonnull final IBDXR2ServiceMetadataProvider aSMPClient)
    {
      return endpointDetailProvider (new AS4EndpointDetailProviderBDXR2 (aSMPClient));
    }

    /**
     * Use this method to explicit set the AP certificate and AP endpoint URL
     * that was retrieved externally (e.g. via an SMP call or for a static test
     * case).
     *
     * @param aCert
     *        The Peppol AP certificate that should be used to encrypt the
     *        message for the receiver. May not be <code>null</code>.
     * @param sDestURL
     *        The destination URL of the receiving AP to send the AS4 message
     *        to. Must be a valid URL and may neither be <code>null</code> nor
     *        empty.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE receiverEndpointDetails (@Nonnull final X509Certificate aCert,
                                                   @Nonnull @Nonempty final String sDestURL)
    {
      return endpointDetailProvider (new AS4EndpointDetailProviderConstant (aCert, sDestURL));
    }

    /**
     * Set an optional Consumer for the retrieved certificate from the endpoint
     * details provider, independent of its usability.
     *
     * @param aCertificateConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE certificateConsumer (@Nullable final Consumer <X509Certificate> aCertificateConsumer)
    {
      m_aCertificateConsumer = aCertificateConsumer;
      return thisAsT ();
    }

    /**
     * Set an optional Consumer for the destination AP address retrieved from
     * the endpoint details provider, independent of its usability.
     *
     * @param aAPEndpointURLConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE endpointURLConsumer (@Nullable final Consumer <String> aAPEndpointURLConsumer)
    {
      m_aAPEndpointURLConsumer = aAPEndpointURLConsumer;
      return thisAsT ();
    }

    /**
     * The effective sending date time of the message. That is set only if
     * message sending takes place.
     *
     * @return The effective sending date time or <code>null</code> if the
     *         messages was not sent yet.
     */
    @Nullable
    public final OffsetDateTime effectiveSendingDateTime ()
    {
      return m_aEffectiveSendingDT;
    }

    protected final boolean isEndpointDetailProviderUsable ()
    {
      // Sender ID doesn't matter here
      if (m_aReceiverID == null)
      {
        LOGGER.warn ("The field 'receiverID' is not set");
        return false;
      }
      if (m_aDocTypeID == null)
      {
        LOGGER.warn ("The field 'docTypeID' is not set");
        return false;
      }
      if (m_aProcessID == null)
      {
        LOGGER.warn ("The field 'processID' is not set");
        return false;
      }
      if (m_aEndpointDetailProvider == null)
      {
        LOGGER.warn ("The field 'endpointDetailProvider' is not set");
        return false;
      }
      return true;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected ESuccess finishFields () throws Phase4Exception
    {
      if (!isEndpointDetailProviderUsable ())
      {
        LOGGER.error ("At least one mandatory field for endpoint discovery is not set and therefore the AS4 message cannot be send.");
        return ESuccess.FAILURE;
      }

      // e.g. SMP lookup. Throws an Phase4Exception in case of error
      m_aEndpointDetailProvider.init (m_aDocTypeID, m_aProcessID, m_aReceiverID);

      // Certificate from e.g. SMP lookup (may throw an exception)
      final X509Certificate aReceiverCert = m_aEndpointDetailProvider.getReceiverAPCertificate ();
      if (m_aCertificateConsumer != null)
      {
        m_aCertificateConsumer.accept (aReceiverCert);
      }
      receiverCertificate (aReceiverCert);

      // URL from e.g. SMP lookup (may throw an exception)
      final String sDestURL = m_aEndpointDetailProvider.getReceiverAPEndpointURL ();
      if (m_aAPEndpointURLConsumer != null)
        m_aAPEndpointURLConsumer.accept (sDestURL);
      endpointURL (sDestURL);

      // From receiver certificate
      toPartyID (PeppolCertificateHelper.getSubjectCN (aReceiverCert));

      // Super at the end
      return super.finishFields ();
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
      if (m_aReceiverID == null)
      {
        LOGGER.warn ("The field 'receiverID' is not set");
        return false;
      }
      if (m_aDocTypeID == null)
      {
        LOGGER.warn ("The field 'docTypeID' is not set");
        return false;
      }
      if (m_aProcessID == null)
      {
        LOGGER.warn ("The field 'processID' is not set");
        return false;
      }

      if (m_aEndpointDetailProvider == null)
      {
        LOGGER.warn ("The field 'endpointDetailProvider' is not set");
        return false;
      }
      // m_aCertificateConsumer may be null
      // m_aAPEndpointURLConsumer may be null

      // All valid
      return true;
    }

    @Override
    protected void customizeBeforeSending () throws Phase4Exception
    {
      // Explicitly remember the old handler
      // Try to do this as close to sending as possible, to avoid that another
      // sendingDateTimConsumer is used
      final IAS4SendingDateTimeConsumer aExisting = m_aSendingDTConsumer;
      sendingDateTimeConsumer (aSendingDT -> {
        // Store in this instance
        m_aEffectiveSendingDT = aSendingDT;

        // Call the original handler
        if (aExisting != null)
          aExisting.onEffectiveSendingDateTime (aSendingDT);
      });
    }
  }

  /**
   * The builder class for sending AS4 messages using DBNAlliance profile
   * specifics. Use {@link #sendMessage()} to trigger the main transmission.
   *
   * @author Philip Helger
   */
  public static class DBNAllianceUserMessageBuilder extends
                                                    AbstractDBNAllianceUserMessageBuilder <DBNAllianceUserMessageBuilder>
  {
    public DBNAllianceUserMessageBuilder ()
    {}
  }
}
