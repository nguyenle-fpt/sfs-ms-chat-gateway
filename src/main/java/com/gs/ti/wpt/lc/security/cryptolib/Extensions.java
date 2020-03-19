package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyExtensionException;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

public final class Extensions
{
	//Certificate extensions
	private final String BASIC_CONSTRAINTS = "basicConstraints";
	private final String KEY_USAGE = "keyUsage";
	private final String EXTENDED_KEY_USAGE = "extendedKeyUsage";
	private final String SUBJECT_KEY_ID = "subjectKeyIdentifier";
	private final String AUTHORITY_KEY_ID = "authorityKeyIdentifier";
	private final String AUTHORITY_INFO_ACCESS = "authorityInfoAccess";
	private final String SUBJECT_INFO_ACCESS = "subjectInfoAccess";
	
	private final String CRL_DISTRIBUTION_POINT = "crlDistributionPoints";
	private final String SUBJECT_ALT_NAME = "subjectAltName";
	private final String ISSUER_ALT_NAME = "issuerAltName";

	private final String POLICY_CONSTRAINTS = "policyConstraints";
	private final String NAME_CONSTRAINTS = "nameConstraints";

	//CRL extensions
	private final String CRL_NUMBER = "crlNumber";

	//CRL entry extensions
	private final String REASON_CODE = "CRLReason";
	private final String INVALIDITY_DATE = "invalidityDate";

	private HashMap<String, String> Ext;
	private static String[] CrlExt = {"CRLReason", "CRLReason", "invalidityDate"};
	private static String[] CrtExt = {"basicConstraints", "keyUsage", "extendedKeyUsage",
			"subjectKeyIdentifier", "subjectInfoAccess", "crlDistributionPoints", "subjectAltName",
			"policyConstraints", "nameConstraints"};

	public Extensions ()
	{
		Ext = new HashMap<String, String>();
	}

	private StringBuffer check (StringBuffer current, String add)
	{
		if (current != null && add != null)
		{
			if (current.length() > 0)
				current.append(", ");
			if (add.length() > 0)
				current.append(add);
		}
		return current; 
	}

  public static String[] getCrlExt()
  {
    return CrlExt;
  }

  public static String[] getCrtExt()
  {
    return CrtExt;
  }

	public HashMap<String, String> getExtensions()
	{
		return Ext;
	}
	
	//Extensions treatment
	public String[] createArrayExt()
	{
		if (Ext != null && Ext.size() > 0)
		{
			HashMap map = Ext;
			Set set = map.entrySet();
			Iterator i = set.iterator();
			int index = 0;
			String[] extensions = new String[Ext.size()];

			while(i.hasNext())
			{
				Map.Entry me = (Map.Entry)i.next();
				String key = me.getKey().toString();
				if (key != null && key.length() > 0)
				{
					String val = me.getValue().toString();
					if (val != null && val.length() > 0)
					{
						extensions[index] = key + "=" + val;
						index ++; 
					}
					else
						break;
				}
				else
					break;
			}

			if (index != map.size())
				extensions = null;

			return extensions;
		}
		return null;
	}

	//Cert extensions
	public void addBasicConstraints (boolean crit, boolean ca, int pathLen) throws
			SymphonyExtensionException
	{
		if (pathLen < 0)
			throw new SymphonyExtensionException("Invalid inputs.");

		StringBuffer val = new StringBuffer("");
		
		if (crit)
			val.append("critical,");
		
		if (ca)
			val.append("CA:TRUE,");
		else
			val.append("CA:FALSE,");

		val.append("pathlen:" + Integer.toString(pathLen));

		Ext.put(BASIC_CONSTRAINTS, val.toString());
	}

	public void addBasicConstraints (boolean crit, boolean ca) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");
		
		if (crit)
			val.append("critical,");
		
		if (ca)
			val.append("CA:TRUE");
		else
			val.append("CA:FALSE");

		Ext.put(BASIC_CONSTRAINTS, val.toString());
	}

	public void addKeyUsage (boolean crit, boolean digitSign, boolean nonRepu, boolean keyEnci, boolean dataEnci, boolean keyAgre, boolean keyCert, boolean crlSign, boolean encipherOnly, boolean decipherOnly) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");
		
		if ((digitSign || nonRepu || keyEnci || dataEnci || keyAgre || keyCert || crlSign || encipherOnly || decipherOnly) == false)
			throw new SymphonyExtensionException("Invalid inputs.");

		if (crit)
			val.append("critical");
		
		if (digitSign)
			val = check(val, "digitalSignature");
		
		if (nonRepu)
			val = check(val, "nonRepudiation");

		if (keyEnci)
			val = check(val, "keyEncipherment");
		
		if (dataEnci)
			val = check(val, "dataEncipherment");
			
		if (keyAgre)
			val = check(val, "keyAgreement");
		
		if (keyCert)
			val = check(val, "keyCertSign");
		
		if (crlSign)
			val = check(val, "cRLSign");

		if (encipherOnly)
			val = check(val, "encipherOnly");

		if (decipherOnly)
			val = check(val, "decipherOnly");
		
		Ext.put(KEY_USAGE, val.toString());
	}

	public void addExtendedKeyUsage (boolean crit, boolean servAuth, boolean clientAuth, boolean codeSign, boolean emailPro, boolean timeSt, boolean OCSPSign, boolean msCodeI, boolean msCodeC, boolean msCTLSign, boolean msEFS) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if ((servAuth || clientAuth || codeSign || emailPro || timeSt || OCSPSign || msCodeC || msCodeI || msCTLSign || msEFS) == false)
			throw new SymphonyExtensionException("Invalid inputs.");

		if (crit)
			val.append("critical");

		if (servAuth)
			val = check(val, "serverAuth");

		if (clientAuth)
			val = check(val, "clientAuth");
		
		if (codeSign)
			val = check(val, "codeSigning");

		if (emailPro)
			val = check(val, "emailProtection");

		if (timeSt)
			val = check(val, "timeStamping");

		if (OCSPSign)
			val = check(val, "OCSPSigning");
		
		if (msCodeI)
			val = check(val, "msCodeInd");

		if (msCodeC)
			val = check(val, "msCodeCom");

		if (msCTLSign)
			val = check(val, "msCTLSign");

		if (msEFS)
			val = check(val, "msEFS");

		Ext.put(EXTENDED_KEY_USAGE, val.toString());
	}

	public void addSubjectKeyID (boolean crit) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical, hash");
		else
			val.append("hash");

		
		Ext.put(SUBJECT_KEY_ID, val.toString());
	}

	public void addAuthorityKeyID (boolean crit, boolean keyid, boolean issuer) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		if (keyid && issuer)
				val = check(val, "keyid:always, issuer:always");
		else
		{
			if (issuer)
				val = check(val, "issuer");

			if (keyid)
				val = check(val, "keyid");
		}

		if (val.length() > 0)
			Ext.put(AUTHORITY_KEY_ID, val.toString());
		else
			throw new SymphonyExtensionException("Invalid inputs.");
	}

	public void addAuthorityInfo (boolean crit, String infoAccess) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");
		
		if (crit)
			val.append("critical,");
		if (infoAccess != null && infoAccess.length() > 0)
			val.append(infoAccess);
		else
			throw new SymphonyExtensionException("Invalid inputs.");

		Ext.put(AUTHORITY_INFO_ACCESS, val.toString());
	}

	public void addSubjectInfo (boolean crit, String infoAccess) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical,");
		if (infoAccess != null && infoAccess.length() > 0)
			val.append(infoAccess);
		else
			throw new SymphonyExtensionException("Invalid inputs.");

		Ext.put(SUBJECT_INFO_ACCESS, val.toString());
	}

	public void addPolicyConstraints (boolean crit, int num1) throws SymphonyExtensionException
	{
		if (num1 < 0)
			throw new SymphonyExtensionException("You can not pass negative values.");

		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		String tmp = "requireExplicitPolicy:" + num1;
		val = check(val, tmp);

		
		Ext.put(POLICY_CONSTRAINTS, val.toString());
	}

	public void addNameConstraints (boolean crit, boolean permitted, String value1, boolean excluded, String value2) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		if (permitted)
		{
			String tmp = "permitted;"+value1;
			val = check(val, tmp);
		}

		if (excluded)
		{
			String tmp = "excluded;"+value2;
			val = check(val, tmp);
		}

		if (val.length() > 0)
			Ext.put(NAME_CONSTRAINTS, val.toString());
		else
			throw new SymphonyExtensionException("Invalid inputs.");
	}

	public void addCRLDistributionPoint (boolean crit, String distributionPoints) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		boolean d = distributionPoints != null && distributionPoints.length() > 0;

		if (d)
			val = check(val, distributionPoints);

		if (val.length() > 0)
			Ext.put(CRL_DISTRIBUTION_POINT, val.toString());
		else
			throw new SymphonyExtensionException("Invalid inputs.");
	}

	public void addSubjectAltName (boolean crit, String subjectAltNames) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		boolean d = subjectAltNames != null && subjectAltNames.length() > 0;

		if (d)
			val = check(val, subjectAltNames);

		if (val.length() > 0)
			Ext.put(SUBJECT_ALT_NAME, val.toString());
		else
			throw new SymphonyExtensionException("Invalid inputs.");
	}

	public void addIssuerAltName (boolean crit, String issuerAltName) throws SymphonyExtensionException
	{
		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical");

		if (issuerAltName != null && issuerAltName.length() > 0)
			val = check(val, issuerAltName);

		if (val.length() > 0)
			Ext.put(ISSUER_ALT_NAME, val.toString());
		else
			throw new SymphonyExtensionException("Invalid inputs.");
	}

	// CRL specific extensions
	public void addCrlNumber (boolean crit, String number) throws SymphonyExtensionException
	{
		if (number == null || number.length() == 0)
			throw new SymphonyExtensionException("CRL Number can not be null or empty.");
		
		StringBuffer val = new StringBuffer("");
		
		if (crit)
			val.append("critical, ");

		val.append(number);

		Ext.put(CRL_NUMBER, val.toString());
	}

	// CRL entry extensions
	public void addReasonCode (boolean crit, String reason) throws SymphonyExtensionException
	{
		if (reason == null || reason.length() == 0 || Integer.parseInt(reason) < 0 || Integer.parseInt(reason) > 10)
			throw new SymphonyExtensionException("Reason code must be between 0 and 10 (included).");

		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical, ");

		val.append(reason);

		Ext.put(REASON_CODE, val.toString());
	}

	public void addInvalidityDate (boolean crit, String date) throws SymphonyExtensionException
	{
		if (date == null || date.length() == 0)
			throw new SymphonyExtensionException("You have to pass a date.");

		StringBuffer val = new StringBuffer("");

		if (crit)
			val.append("critical, ");

		val.append(date);

		Ext.put(INVALIDITY_DATE, val.toString());
	}
}
