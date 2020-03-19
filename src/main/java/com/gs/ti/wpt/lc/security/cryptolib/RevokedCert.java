package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyRevokedCertException;

import java.text.SimpleDateFormat;
import java.util.Date;


public final class RevokedCert
{
	//Attributes
	private String serial;
	private String revDate;
	private String[] trExt;

	//Constructor
	public RevokedCert()
	{
		serial = null;
		revDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
	}

	//Getter
	public String getSerial()
	{
		return serial;
	}

	public String getRevDate()
	{
		return revDate;
	}

	public String[] getExt()
	{
		return trExt;
	}

	//Setter
	public void setExtensions(Extensions extension) throws SymphonyRevokedCertException
	{
		if (extension != null && extension.getExtensions().size() > 0)
			trExt = extension.createArrayExt();
		else
			throw new SymphonyRevokedCertException("Entry extensions can not be null or empty.");
			
	}

	public void setSerial(String seri) throws SymphonyRevokedCertException
	{
		if (seri != null && seri.length() > 0)
			serial = seri;
		else
			throw new SymphonyRevokedCertException("Serial number can not be null or empty for a revoked certificate.");
	}
}
