package org.macmillan.core.cronjob.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;


import org.apache.log4j.Logger;
import org.macmillan.core.enums.ExportQueryType;
import org.macmillan.generated.core.model.MacmillanExportCronJobModel;
import org.springframework.beans.factory.annotation.Autowired;

import de.hybris.platform.acceleratorservices.email.EmailService;
import de.hybris.platform.acceleratorservices.model.email.EmailAddressModel;
import de.hybris.platform.acceleratorservices.model.email.EmailAttachmentModel;
import de.hybris.platform.acceleratorservices.model.email.EmailMessageModel;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.impex.jalo.exp.Export;
import de.hybris.platform.impex.jalo.exp.ImpExExportMedia;
import de.hybris.platform.impex.jalo.exp.converter.DefaultExportResultHandler;
import de.hybris.platform.jalo.JaloBusinessException;
import de.hybris.platform.jalo.user.Title;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.impex.ExportResult;
import de.hybris.platform.servicelayer.impex.ExportService;
import de.hybris.platform.servicelayer.impex.ImpExResource;
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource;
import de.hybris.platform.servicelayer.internal.converter.util.ModelUtils;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.search.impl.LazyLoadMultiColumnModelList;
import de.hybris.platform.util.CSVConstants;
import de.hybris.platform.util.Config;

public class MacmillanDataExportCronjob extends AbstractJobPerformable<MacmillanExportCronJobModel>{
	private static final Logger LOG = Logger.getLogger(MacmillanDataExportCronjob.class);
	
	@Autowired
	private ExportService exportService;
	@Autowired
	private ModelService modelService;
	
	@Autowired
	private EmailService emailService;
		
	@Autowired
	private MediaService mediaService;
	
	@Autowired
	private CatalogVersionService catalogVersionService;
	
	@Autowired
	private FlexibleSearchService flexibleSearchService;
	@Override
	public PerformResult perform(MacmillanExportCronJobModel cronjob) {
		
		try {
			if(cronjob.getExportQueryType().equals(ExportQueryType.IMPEX)){
					exportDataAction(cronjob);
			}else{
				exportFlexiQueryDataAction(cronjob);
			}
		} catch (IOException | JaloBusinessException e) {
			e.printStackTrace();
			return new PerformResult(CronJobResult.ERROR, CronJobStatus.ABORTED);
		}
		
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}
	
	

	private void exportFlexiQueryDataAction(MacmillanExportCronJobModel cronjob) {
		
		final String flexiQuery = cronjob.getFlexiQuery();
		final FlexibleSearchQuery flexibleSearchQuery = new FlexibleSearchQuery(flexiQuery);
		flexibleSearchQuery.setResultClassList(Arrays.asList(String.class,String.class,String.class,String.class,String.class,String.class));
		LOG.info("after flext search");
		final SearchResult<List<?>> searchResult = flexibleSearchService.search(flexibleSearchQuery);
		LOG.info("Search result string:::"+searchResult.toString());
		LOG.info("after search result"+searchResult.getTotalCount());
		final List list = (List) ModelUtils.getFieldValue(searchResult, "resultList");
		LOG.info("after list conversion::"+list.size());
		for (int i = 0; i < searchResult.getTotalCount(); i++)
		{
			final List row = (List) list.get(i);
			LOG.info("row--->"+row.toString());
			
		}
		
		sendEmail(cronjob,list.toString());
	}



	private void sendEmail(MacmillanExportCronJobModel cronjob,String output) {
		EmailAttachmentModel attachment= createEmailAttachment(cronjob,output);
		final String emailAddress = cronjob.getToAddress();
		List<EmailAddressModel> listEmailModel = new ArrayList<>();
		if(emailAddress.contains(",")){
			String[] address=emailAddress.split(",");
			for (String email : address) {
				final EmailAddressModel toAddress = new EmailAddressModel();
				toAddress.setEmailAddress(email);
				toAddress.setDisplayName(email);
				listEmailModel.add(toAddress);
			}
		}else{
			final EmailAddressModel toAddress = new EmailAddressModel();
			toAddress.setEmailAddress(emailAddress);
			toAddress.setDisplayName(emailAddress);
			listEmailModel.add(toAddress);
		}
		final EmailAddressModel fromAddress = new EmailAddressModel();
		fromAddress.setEmailAddress(Config.getParameter("from.address"));
		fromAddress.setDisplayName("student store");
		
		final EmailMessageModel emailMessageModel = new EmailMessageModel();
		emailMessageModel.setToAddresses(listEmailModel);
		emailMessageModel.setCcAddresses(null);
		emailMessageModel.setBccAddresses(null);
		emailMessageModel.setFromAddress(fromAddress);
		emailMessageModel.setReplyToAddress(fromAddress.getEmailAddress());
		emailMessageModel.setSubject("Export Data");
		emailMessageModel.setAttachments(Collections.singletonList(attachment));
		emailMessageModel.setBody("Please find the attachement.");
		//final EmailMessageModel message = emailService.createEmailMessage(listEmailModel, null, null,
			//	fromAddress, "StudentStore-Do-Not-Reply@macmillan.com", "subject", "body", Collections.singletonList(attachment));
		final boolean result = emailService.send(emailMessageModel);
		
		LOG.info("email send::"+result);
		
	}



	private EmailAttachmentModel createEmailAttachment(MacmillanExportCronJobModel cronjob,String output) {
		byte[] bytes = output.getBytes();
		InputStream inputData = new ByteArrayInputStream(bytes);
		
		final DataInputStream dataInputStream = new DataInputStream(inputData);
		//EmailAttachmentModel emailAttachmentModel = new EmailAttachmentModel();
		String fileName = new Date()+"_"+cronjob.getExportFileName();
		final EmailAttachmentModel attachment = modelService.create(EmailAttachmentModel.class);
		attachment.setCode(fileName);
		attachment.setMime("text/csv");
		attachment.setRealFileName(fileName);
		attachment.setCatalogVersion(catalogVersionService.getCatalogVersion("macmillanContentCatalog", "Online"));
		modelService.save(attachment);
		mediaService.setStreamForMedia(attachment, dataInputStream, fileName, "text/csv", null);
		return attachment;
	}



	private void exportDataAction(MacmillanExportCronJobModel cronjob) throws IOException, JaloBusinessException {
		//cronjob.getImpexScript();
		InputStream inputstream = new FileInputStream(Config.getParameter("export.data.query")+cronjob.getSourceFileName());
		final ImpExResource res = new StreamBasedImpExResource(inputstream, CSVConstants.HYBRIS_ENCODING);
		LOG.info("query-->"+res.toString());
		StringBuilder productSb = exportData(res,cronjob.getExportFileName());
		sendEmail(cronjob,productSb.toString());
		
	}



	private StringBuilder exportData(ImpExResource res,String fileName) throws IOException, JaloBusinessException{
		final ExportResult result = exportService.exportData(res);
		final DefaultExportResultHandler handler = new DefaultExportResultHandler();
		handler.setExport((Export) modelService.getSource(result.getExport()));
		
		final List<ZipEntry> entries = handler.getZipEntries((ImpExExportMedia) modelService.getSource(result.getExportedData()));
		StringBuilder resultMesg = new StringBuilder();

		for (final ZipEntry entry : entries)
		{
			if (entry.getName().equals(fileName))
			{
				resultMesg = handler.getDataContent(entry);
			}
		}
		LOG.info("result message::"+resultMesg.toString());
		return resultMesg;
	}
	}
