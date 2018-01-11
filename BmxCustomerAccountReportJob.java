package com.biomerieux.core.cronjob;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;

import de.hybris.platform.b2b.model.B2BCustomerModel;
import de.hybris.platform.b2b.model.B2BUnitModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.security.PrincipalModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.util.Config;
/**
 *	shivaCustomerAccountReportJob
 */
public class shivaCustomerAccountReportJob extends AbstractJobPerformable<CronJobModel>
{

	private static final Logger LOG = Logger.getLogger(shivaCustomerAccountReportJob.class);
	
	private static final String CSVTEXT = ".csv";
	private static final String FILENAME = "BiomerieuxDirect CustomerAccount Report";
	private static final String FILENAMEDATEFORMAT = "ddMMMyyyy";
	private final String delimiterOutput = ",";
	private static final String ACCOUNT_NAME = "Account Name";
	private static final String SAP_ACCOUNT = "SAP Account";
	private static final String SHEET_NAME = "CustomerAccounts";
	private static final String MEMBERS_OF_ACCOUNT = "Members of Account";
	private static final String shiva_CUSTOMERS = "shivaCustomers";
	
	@Resource
	ConfigurationService configurationService;
	
	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		try
		{
			final B2BUnitModel b2bUnitModel = new B2BUnitModel();
			LOG.info("Invoking shivaCustomerAccountReportJob");
			b2bUnitModel.setActive(Boolean.TRUE);
			final List<B2BUnitModel> b2bUnitModelList = flexibleSearchService.getModelsByExample(b2bUnitModel);
			if (b2bUnitModelList != null && b2bUnitModelList.size() > 0)
			{
				FileOutputStream fileOutputStream;
				final String stamp = new SimpleDateFormat(FILENAMEDATEFORMAT).format(new Date());
				final String filename = FILENAME + "-" + stamp + CSVTEXT;
				File b2bUnitReportFile=new File (filename);
		        HSSFWorkbook workbook = new HSSFWorkbook();
		        HSSFSheet sheet = workbook.createSheet(SHEET_NAME);  
		        HSSFRow rowHead = sheet.createRow((short)0);
		        rowHead.createCell(0).setCellValue(SAP_ACCOUNT);
		        rowHead.createCell(1).setCellValue(ACCOUNT_NAME);
		        rowHead.createCell(2).setCellValue(MEMBERS_OF_ACCOUNT);
		        
		        int rowCount=1;
				for(B2BUnitModel newB2BUnitModel:b2bUnitModelList)
				{
					if(newB2BUnitModel.getGroups()!=null && newB2BUnitModel.getGroups().size()>0)
					{
						rowCount = createRowsForWorksheet(sheet, rowCount,
								newB2BUnitModel);
					}
				}
				
				try {
					fileOutputStream = new FileOutputStream(b2bUnitReportFile);
					workbook.write(fileOutputStream);
					fileOutputStream.close();
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
				catch (IOException e1) {
					e1.printStackTrace();
				}
				
				//Send email with attachment (Customer Report)
				sendEmailwithAttachment(cronJob, filename, b2bUnitReportFile);
			}
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error(e.getMessage(), e);
			return new PerformResult(CronJobResult.FAILURE, CronJobStatus.FINISHED);
		}
		catch (final MessagingException me)
		{
			LOG.error(me.getMessage(), me);
			return new PerformResult(CronJobResult.FAILURE, CronJobStatus.FINISHED);
		}
		catch (final IOException ioe)
		{
			LOG.error(ioe.getMessage(), ioe);
			return new PerformResult(CronJobResult.FAILURE, CronJobStatus.FINISHED);
		}
		catch (final EmailException emailExce)
		{
			LOG.error(emailExce.getMessage(), emailExce);
			return new PerformResult(CronJobResult.FAILURE, CronJobStatus.FINISHED);
		}
		catch (final Exception e)
		{
			LOG.error(e.getMessage(), e);
			return new PerformResult(CronJobResult.FAILURE, CronJobStatus.FINISHED);
		}

		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	private int createRowsForWorksheet(HSSFSheet sheet, int rowCount,
			B2BUnitModel newB2BUnitModel) {
		for(PrincipalGroupModel principalGroupModel:newB2BUnitModel.getGroups())
		{
			if(!principalGroupModel.getUid().equals(shiva_CUSTOMERS))
			{
				HSSFRow row = sheet.createRow((short)rowCount);
				
				if(newB2BUnitModel.getUid()!=null)
				{
		        row.createCell(0).setCellValue(newB2BUnitModel.getUid().substring(9, newB2BUnitModel.getUid().length()));
				}
				if(newB2BUnitModel.getLocName()!=null)
				{
				row.createCell(1).setCellValue(newB2BUnitModel.getLocName().substring(7, newB2BUnitModel.getLocName().length()));
				}
				if(newB2BUnitModel.getMembers()!=null && newB2BUnitModel.getMembers().size() > 0)
				{
					List<String>  customers=new ArrayList<String>();
					if(newB2BUnitModel.getMembers()!=null && newB2BUnitModel.getMembers().size()>0)
					{
						for(PrincipalModel principalModel:newB2BUnitModel.getMembers())
						{
							if(principalModel instanceof B2BCustomerModel)
							{
								customers.add(((B2BCustomerModel)principalModel).getUid());
							}
						}
						row.createCell(2).setCellValue(customers.toString());
					}
				}
				
				rowCount++;
			}
		}
		return rowCount;
	}

	private void sendEmailwithAttachment(final CronJobModel cronJob,
			final String filename, File b2bUnitReportFile)
			throws EmailException, IOException, MessagingException {
		
		Collection<String> toAddressesList=Arrays.asList(cronJob.getEmailAddress().split(delimiterOutput));
		Collection<InternetAddress> internetAddressList=new ArrayList<InternetAddress>();
		for(String toAddress:toAddressesList)
		{
			InternetAddress internetAddress=new InternetAddress(toAddress);
			internetAddressList.add(internetAddress);
		}
		EmailAttachment attachment=new EmailAttachment();
		attachment.setDescription(b2bUnitReportFile.getName());
		attachment.setDisposition(EmailAttachment.ATTACHMENT);
		attachment.setName(b2bUnitReportFile.getName());
		attachment.setPath(b2bUnitReportFile.getPath());
		final MultiPartEmail email = new MultiPartEmail();
		email.setHostName(configurationService.getConfiguration().getString(Config.Params.MAIL_SMTP_SERVER));
		email.setTo(internetAddressList);
		email.setFrom(configurationService.getConfiguration().getString(Config.Params.MAIL_FROM));
		email.setSubject(b2bUnitReportFile.getName());
		email.setMsg("Find attached customer account report file of the month: "+filename+"\n\n");
		email.attach(attachment);
		email.send();
		LOG.info("Email send successfully with attachment");
	}

}
