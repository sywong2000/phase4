/**
 * Copyright (C) 2020 Philip Helger (www.helger.com)
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
package com.helger.phase4.cef;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.string.StringHelper;
import com.helger.peppol.smp.ESMPTransportProfile;
import com.helger.peppol.smp.ISMPTransportProfile;
import com.helger.peppolid.IDocumentTypeIdentifier;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.peppolid.IProcessIdentifier;
import com.helger.smpclient.bdxr2.BDXR2ClientReadOnly;
import com.helger.smpclient.bdxr2.IBDXR2ServiceMetadataProvider;
import com.helger.smpclient.exception.SMPClientException;
import com.helger.xsds.bdxr.smp2.ac.EndpointType;

/**
 * The default implementation of {@link IPhase4CEFEndpointDetailProvider} using
 * an OASIS BDXR SMP v2 Client to determine this information from an endpoint.
 *
 * @author Philip Helger
 * @since 0.10.6
 */
public class Phase4CEFEndpointDetailProviderBDXR2 implements IPhase4CEFEndpointDetailProvider
{
  public static final ISMPTransportProfile DEFAULT_TRANSPORT_PROFILE = ESMPTransportProfile.TRANSPORT_PROFILE_BDXR_AS4;

  private static final Logger LOGGER = LoggerFactory.getLogger (Phase4CEFEndpointDetailProviderBDXR2.class);

  private final IBDXR2ServiceMetadataProvider m_aSMPClient;
  private ISMPTransportProfile m_aTP = DEFAULT_TRANSPORT_PROFILE;
  private EndpointType m_aEndpoint;

  public Phase4CEFEndpointDetailProviderBDXR2 (@Nonnull final IBDXR2ServiceMetadataProvider aSMPClient)
  {
    ValueEnforcer.notNull (aSMPClient, "SMPClient");
    m_aSMPClient = aSMPClient;
  }

  /**
   * @return The service metadata provider passed in the constructor. Never
   *         <code>null</code>.
   */
  @Nonnull
  public final IBDXR2ServiceMetadataProvider getServiceMetadataProvider ()
  {
    return m_aSMPClient;
  }

  /**
   * @return The transport profile to be used. Defaults to
   *         {@link #DEFAULT_TRANSPORT_PROFILE}.
   */
  @Nonnull
  public final ISMPTransportProfile getTransportProfile ()
  {
    return m_aTP;
  }

  /**
   * Change the transport profile to be used. This only has an effect if it is
   * called prior to
   * {@link #init(IDocumentTypeIdentifier, IProcessIdentifier, IParticipantIdentifier)}.
   *
   * @param aTP
   *        The transport profile to be used. May not be <code>null</code>.
   * @return this for chaining.
   */
  @Nonnull
  public final Phase4CEFEndpointDetailProviderBDXR2 setTransportProfile (@Nonnull final ISMPTransportProfile aTP)
  {
    ValueEnforcer.notNull (aTP, "TransportProfile");
    m_aTP = aTP;
    return this;
  }

  /**
   * @return The endpoint resolved. May only be non-<code>null</code> if
   *         {@link #init(IDocumentTypeIdentifier, IProcessIdentifier, IParticipantIdentifier)}
   *         was called.
   */
  @Nullable
  public final EndpointType getEndpoint ()
  {
    return m_aEndpoint;
  }

  public void init (@Nonnull final IDocumentTypeIdentifier aDocTypeID,
                    @Nonnull final IProcessIdentifier aProcID,
                    @Nonnull final IParticipantIdentifier aReceiverID) throws Phase4CEFException
  {
    ValueEnforcer.notNull (aDocTypeID, "DocTypeID");
    ValueEnforcer.notNull (aProcID, "ProcID");
    ValueEnforcer.notNull (aReceiverID, "ReceiverID");

    // Do the real SMP lookup only once
    if (m_aEndpoint == null)
    {
      // Perform SMP lookup
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Start performing SMP lookup (" +
                      aReceiverID.getURIEncoded () +
                      ", " +
                      aDocTypeID.getURIEncoded () +
                      ", " +
                      aProcID.getURIEncoded () +
                      ")");

      // Perform SMP lookup
      try
      {
        m_aEndpoint = m_aSMPClient.getEndpoint (aReceiverID, aDocTypeID, aProcID, m_aTP);
        if (m_aEndpoint == null)
          throw new Phase4CEFSMPException ("Failed to resolve SMP endpoint (" +
                                           aReceiverID.getURIEncoded () +
                                           ", " +
                                           aDocTypeID.getURIEncoded () +
                                           ", " +
                                           aProcID.getURIEncoded () +
                                           ", " +
                                           m_aTP.getID () +
                                           ")");

        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Successfully resolved SMP endpoint (" +
                        aReceiverID.getURIEncoded () +
                        ", " +
                        aDocTypeID.getURIEncoded () +
                        ", " +
                        aProcID.getURIEncoded () +
                        ", " +
                        m_aTP.getID () +
                        ")");
      }
      catch (final SMPClientException ex)
      {
        throw new Phase4CEFSMPException ("Failed to resolve SMP endpoint (" +
                                         aReceiverID.getURIEncoded () +
                                         ", " +
                                         aDocTypeID.getURIEncoded () +
                                         ", " +
                                         aProcID.getURIEncoded () +
                                         ", " +
                                         m_aTP.getID () +
                                         ")",
                                         ex);
      }
    }
  }

  @Nullable
  public X509Certificate getReceiverAPCertificate () throws Phase4CEFException
  {
    try
    {
      return BDXR2ClientReadOnly.getEndpointCertificate (m_aEndpoint);
    }
    catch (final CertificateException ex)
    {
      throw new Phase4CEFException ("Failed to extract AP certificate from SMP endpoint: " + m_aEndpoint, ex);
    }
  }

  @Nonnull
  @Nonempty
  public String getReceiverAPEndpointURL () throws Phase4CEFException
  {
    final String sDestURL = BDXR2ClientReadOnly.getEndpointAddress (m_aEndpoint);
    if (StringHelper.hasNoText (sDestURL))
      throw new Phase4CEFException ("Failed to determine the destination URL from the SMP endpoint: " + m_aEndpoint);
    return sDestURL;
  }
}