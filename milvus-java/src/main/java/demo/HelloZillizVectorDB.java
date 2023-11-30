package demo;


import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.E5SmallV2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import util.PropertyFilesUtil;

import java.util.*;


public class HelloZillizVectorDB {
    public static void main(String[] args) {
        // connect to milvus
        final MilvusServiceClient milvusClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withUri(PropertyFilesUtil.getRunValue("uri"))
                        .withAuthorization(PropertyFilesUtil.getRunValue("user"), PropertyFilesUtil.getRunValue("password"))
                        .build());
        System.out.println("Connecting to DB: " + PropertyFilesUtil.getRunValue("uri"));

        // Check if the collection exists
        String collectionName = "documents";
        R<DescribeCollectionResponse> responseR = milvusClient
                .describeCollection(DescribeCollectionParam
                        .newBuilder()
                        .withCollectionName(collectionName)
                        .build());

        if (responseR.getData() != null) {
            milvusClient.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collectionName).build());
        }
        System.out.println("Success!");

        // create a collection with customized primary field: doc_id_field
        int dim = 384;
        FieldType bookIdField = FieldType.newBuilder()
                .withName("doc_id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        FieldType wordCountField = FieldType.newBuilder()
                .withName("char_count")
                .withDataType(DataType.Int64)
                .build();
        FieldType docText = FieldType.newBuilder()
                .withName("doc_text")
                .withDataType(DataType.VarChar)
                .withMaxLength(510)
                .withDimension(dim)
                .build();
        FieldType bookIntroField = FieldType.newBuilder()
                .withName("doc_text_vector")
                .withDataType(DataType.FloatVector)
                .withDimension(dim)
                .build();

        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Document Knowledge Base")
                .withShardsNum(2)
                .addFieldType(bookIdField)
                .addFieldType(wordCountField)
                .addFieldType(docText)
                .addFieldType(bookIntroField)
                .build();

        System.out.println("Creating example collection: " + collectionName);
        System.out.println("Schema: " + createCollectionParam);
        milvusClient.createCollection(createCollectionParam);
        System.out.println("Success!");

//        insert data with customized ids
        List<Long> doc_id_array = new ArrayList<>();
        List<Long> char_count_array = new ArrayList<>();
        List<List<Float>> doc_text_vector_array = new ArrayList<>();
        List<String> doc_text_array = new ArrayList<>();

        String text = "  This Lease is valid only if flled out before January 1, 2024.\n" +
                "Apartment Lease Contract\n" +
                "This is a binding contract. Read carefully before signing.\n" +
                "This Lease Contract (“Lease”) is between you, the resident(s) as listed below and us. The terms “you” and “your” refer to all residents.\n" +
                "The terms “we,” “us,” and “our” refer to the owner listed below.\n" +
                "PARTIES\n" +
                "Residents _D_e_e__p_s_o_n___K_h_a__d_k_a___________________________ ______________________________________________________ ______________________________________________________ ______________________________________________________ ______________________________________________________ ______________________________________________________ ______________________________________________________\n" +
                "Owner _B_9___M_F___I_C_G_4___H_o_l__d_i_n__g_s__L__L_C________________ ____________________________________________________ ____________________________________________________ ____________________________________________________\n" +
                "Occupants _S_a_n__j_e_e_v___T_h__a_k_u_r_________________________ ____________________________________________________ ____________________________________________________ ____________________________________________________ ____________________________________________________\n" +
                "______________________________________________________\n" +
                "_______________________________________________________\n" +
                "______________________________________________________ ______________________________________________________\n" +
                "LEASE DETAILS\n" +
                " A. Apartment (Par. 2)\n" +
                "Street Address: _3_9__0_4___T_a_l__l_w_o__o_d___D_r___#_7_1__6________________________________________________________________________ Apartment No. ___________7_1__6___________ City: _______________E__u_l_e__s_s________________ State: _T_X_ Zip: _______7_6__0_4_0________\n" +
                " B. Initial Lease Term. Begins:____________0__7_/_1__6_/_2__0_2_2_____________ Ends at 11:59 p.m. on:__________0__5_/_0__8_/_2__0_2_3___________\n" +
                " C. Monthly Base Rent (Par. 3)\n" +
                "$ _1_4__5_9__._0_0__________________\n" +
                "   E. Security Deposit (Par. 5)\n" +
                "$ _0_.__0_0_______________________\n" +
                "Note that this amount does not include any Animal Deposit, which would be refected in an Animal Addendum.\n" +
                "F. Notice of Termination or Intent to Move Out (Par. 4)\n" +
                "A minimum of _________6_0__________ days’ written notice of termination or intent to move out required at end of initial Lease term or during renewal period\n" +
                "If the number of days isn’t flled in, notice of at least 30 days is required.\n" +
                "D. Prorated Rent\n" +
                "$ ____________________________ rX due for the remainder of 1st\n" +
                "month or rfor 2nd month\n" +
                "753.03\n" +
                " G. Late Fees (Par. 3.3)\n" +
                "Initial Late Fee\n" +
                "rX ____1_0_____% of one month’s monthly base rent or\n" +
                "r $ _____________________\n" +
                "Dueifrentunpaidby11:59p.m.onthe ________________3_r__d________________(3rdorgreater)dayofthemonth\n" +
                "Daily Late Fee\n" +
                "r _________ % of one month’s monthly base rent for _______ days or r $ _________________________________ for _____ days\n" +
                " H. Returned Check or Rejected Payment Fee (Par. 3.4)\n" +
                "$ ____________________________\n" +
                "50.00\n" +
                "   J. Optional Early Termination Fee (Par. 7.2)\n" +
                "$ _2_9__1_8__._0_0__________________________ Notice of ______6__0_______ days is required.\n" +
                "You are not eligible for early termination if you are in default.\n" +
                "Fee must be paid no later than ____3_0_____ days after you give us notice\n" +
                "If values are blank or “0,” then this section does not apply.\n" +
                "K. Animal Violation Charge (Par. 12.2)\n" +
                "Initial charge of $ _1_0__0_.__0_0_________ per animal (not to exceed $100 per animal) and\n" +
                "A daily charge of $ _1_0__._0__0____________ per animal (not to exceed $10 per day per animal)\n" +
                "I. Reletting Charge (Par. 7.1)\n" +
                "A reletting charge of $ _1_2_4__0_.__1_5__ (not to exceed 85% of the highest monthly Rent during the Lease term) may be charged in certain default situations\n" +
                " L. Additional Rent - Monthly Recurring Fixed Charges. You will pay separately for these items as outlined below and/or in separate addenda, Special Provisions or an amendment to this Lease.\n" +
                "Animal rent\n" +
                "Internet\n" +
                "Storage\n" +
                "Other: ________________________________________________________________________________________ Other: ________________________________________________________________________________________ Other: ________________________________________________________________________________________ Other: ________________________________________________________________________________________\n" +
                "$ _0_.__0_0_______________________ Cable/satellite $ _0_.__0_0__________________ Concierge trash $ _8_.__2_5______________ $_0_.__0_0________________________ Package service $ _0_.__0_0__________________ Pest control $ _3_.__0_0______________ $ ____________________________ Stormwater/drainage $ ___________________ Washer/Dryer $ ___________________\n" +
                "$ ______________________ $ ______________________ $ ______________________ $ ______________________\n" +
                " M. Other Variable Charges. You will pay separately for gas, water, wastewater, electricity, trash/recycling, utility billing fees and other items as outlined in separate addenda, Special Provisions or an amendment to this Lease.\n" +
                "UtilityConnectionChargeorTransferFee:$ ______5__0_.__0_0_______ (nottoexceed$50)tobepaidwithin5daysofwrittennotice(Par.3.5)\n" +
                " Special Provisions. See Par. 32 or additional addenda attached. The Lease cannot be changed unless in writing and signed by you and us.\n" +
                "Apartment Lease Contract ©2022, Texas Apartment Association, Inc.\n" +
                "Page 1 of 6\n" +
                "$H - I$ $AdultOcc - I1$ $e-Doc Signer - I$\n" +
                "\n" +
                " LEASE TERMS AND CONDITIONS\n" +
                "1. Defnitions. The following terms are commonly used in this Lease:\n" +
                "1.1. “Residents” are those listed in “Residents” above who sign\n" +
                "the Lease and are authorized to live in the apartment.\n" +
                "1.2. “Occupants” are those listed in this Lease who are also autho- rized to live in the apartment, but who do not sign the Lease.\n" +
                "1.3. “Owner” may be identifed by an assumed name and is the owner only and not property managers or anyone else.\n" +
                "1.4. “Including” in this Lease means “including but not limited to.”\n" +
                "1.5. “Community Policies” are the written apartment rules and policies, including property signage and instructions for care of our property and amenities, with which you, your occupants, and your guests must comply.\n" +
                "1.6. “Rent” is monthly base rent plus additional monthly recurring fxed charges.\n" +
                "2. Apartment. You are leasing the apartment listed above for use as a private residence only.\n" +
                "2.1. Access. In accordance with our Community Policies, you’ll receive access information or devices for your apartment\n" +
                "and mailbox, and other access devices including: __________ _______________________________________________ ______________________________________________.\n" +
                "2.2. Measurements. Any dimensions and sizes provided to you relating to the apartment are only approximations or estimates; actual dimensions and sizes may vary.\n" +
                "2.3. Representations. You agree that designations or accredi- tations associated with the property are subject to change.\n" +
                "3. Rent. You must pay your Rent on or before the 1st day of each month (due date) without demand. There are no exceptions regarding the payment of Rent, and you agree not paying Rent on or before the 1st of each month is a material breach of this Lease.\n" +
                "3.1. Payments. You will pay your Rent by any method, manner and place we specify in accordance with our Community Policies. Cash is not acceptable without our prior written permission. You cannot withhold or ofset Rent unless authorized by law. We may, at our option, require at any time that you pay Rent and other sums due in one single payment by any method we specify.\n" +
                "3.2. Application of Payments. Payment of each sum due is an independent covenant, which means payments are due regardless of our performance. When we receive money, other than water and wastewater payments subject to government regulation, we may apply it at our option and without notice frst to any of your unpaid obligations, then to accrued rent. We may do so regardless of notations on checks or money orders and regardless of when the obligations arose. All sums other than Rent and late fees are due upon our demand. After the due date, we do not have to accept any payments.\n" +
                "3.3. Late Fees. If we don’t receive your monthly base rent in full when it’s due, you must pay late fees as outlined in Lease Details.\n" +
                "3.4. Returned Payment Fee. You’ll pay the fee listed in Lease Details for each returned check or rejected electronic payment, plus initial and daily late fees if applicable, until we receive full payment in an acceptable method.\n" +
                "3.5. Utilities and Services. You’ll pay for all utilities and services, related deposits, and any charges or fees when they are due and as outlined in this Lease. Television channels that are provided may be changed during the Lease term if the change applies to all residents.\n" +
                "If your electricity is interrupted, you must use only battery- operated lighting (no fames). You must not allow any\n" +
                "utilities (other than cable or Internet) to be cut of or\n" +
                "switched for any reason—including disconnection for not paying your bills—until the Lease term or renewal period ends. If a utility is individually metered, it must be connected in your name and you must notify the provider of your move- out date. If you delay getting service turned on in your name by the Lease’s start date or cause it to be transferred back into our name before you surrender or abandon the apartment, you’ll be liable for the charge listed above (not to exceed $50 per billing period), plus the actual or estimated cost of the utilities used while the utility should have been billed to you. If your apartment is individually metered and you change your retail electric provider, you must give us written notice. You must pay all applicable provider fees, including any fees to change service back into our name after you move out.\n" +
                "3.6. Lease Changes. Lease changes are only allowed during the Lease term or renewal period if governed by Par. 10, specifed\n" +
                "in Special Provisions in Par. 32, or by a written addendum or amendment signed by you and us. At or after the end of the initial Lease term, Rent increases will become efective with at least 5 days plus the number of days’ advance notice contained in Box F on page 1 in writing from us to you. Your new Lease, which may include increased Rent or Lease changes, will begin on the date stated in any advance notice we provide (without needing your signature) unless you give us written move-out notice under Par. 25, which applies only to the end of the current Lease term or renewal period.\n" +
                "Apartment Lease Contract ©2022, Texas Apartment Association, Inc.\n" +
                "4. Automatic Lease Renewal and Notice of Termination. This Lease will automatically renew month-to-month unless either party gives written notice of termination or intent to move out as required by Par. 25 and specifed on page 1. If the number of days isn’t flled in, no- tice of at least 30 days is required.\n" +
                "5. Security Deposit. The total security deposit for all residents is due on or before the date this Lease is signed. Any animal deposit will be designated in an animal addendum. Security deposits may not be ap- plied to Rent without our prior written consent.\n" +
                "5.1. Refunds and Deductions. You must give us your advance notice of move out as provided by Par. 25 and forwarding address in writing to receive a written description and itemized list of charges or refund. In accordance with our Community Policies and as allowed by law, we may deduct from your security deposit any amounts due under the Lease. If you move out early or in response to a notice to vacate, you’ll be liable for rekeying charges. Upon receipt of your move-out date and forwarding address in writing, the security deposit will be returned (less lawful deductions)\n" +
                "with an itemized accounting of any deductions, no later than 30 days after surrender or abandonment, unless laws provide otherwise. Any refund may be by one payment jointly payable to all residents and distributed to any one resident we choose, or distributed equally among all residents.\n" +
                "6. Insurance. Our insurance doesn’t cover the loss of or damage to your personal property. You will be required to have liability insur- ance as specifed in our Community Policies or Lease addenda un- less otherwise prohibited by law. If you have insurance covering the apartment or your personal belongings at the time you or we sufer or allege a loss, you agree to require your insurance carrier to waive any insurance subrogation rights. Even if not required, we urge you to obtain your own insurance for losses due to theft, fre, food, water, pipe leaks and similar occurrences. Most renter’s insurance policies don’t cover losses due to a food.\n" +
                "7. Reletting and Early Lease Termination. This Lease may not be ter- minated early except as provided in this Lease.\n" +
                "7.1. Reletting Charge. You’ll be liable for a reletting charge as listed in Lease Details, (not to exceed 85% of the highest monthly Rent during the Lease term) if you: (A) fail to move in, or fail to give written move-out notice as required in Par. 25; (B) move out without paying Rent in full for the entire Lease term or renewal period; (C) move out at our demand because of your default; or (D) are judicially evicted. The reletting charge is not a termination, cancellation or buyout fee and does not release you from your obligations under this Lease, including liability for future or past-due Rent, charges for damages or other sums due.\n" +
                "The reletting charge is a liquidated amount covering only part of our damages—for our time, efort, and expense in fnding and processing a replacement resident. These damages are uncertain and hard to ascertain—particularly those relating to inconvenience, paperwork, advertising, showing apartments, utilities for showing, checking pros- pects, overhead, marketing costs, and locator-service fees. You agree that the reletting charge is a reasonable estimate of our damages and that the charge is due whether or not our reletting attempts succeed.\n" +
                "7.2. Early Lease Termination Procedures. In addition to your termination rights referred to in 7.3 or 8.1 below, if this provision applies under Lease Details, you may terminate the Lease prior to the end of the Lease term if all of the following occur: (a) as outlined in Lease Details, you give us written notice of early termination, pay the early termination fee and specify the date by which you’ll move out; (b) you are not in default at any time and do not hold over; and (c) you repay all rent concessions, credits or discounts you received during the Lease term. If you are in default, the Lease remedies apply.\n" +
                "7.3. Special Termination Rights. You may have the right under Texas law to terminate the Lease early in certain situations involving military deployment or transfer, family violence, certain sexual ofenses, stalking or death of a sole resident.\n" +
                "8. Delay of Occupancy. We are not responsible for any delay of your occupancy caused by construction, repairs, cleaning, or a previous resident’s holding over. This Lease will remain in force subject to\n" +
                "(1) abatement of Rent on a daily basis during delay, and (2) your right to terminate the Lease in writing as set forth below. Rent abatement and Lease termination do not apply if the delay is for cleaning or re- pairs that don’t prevent you from moving into the apartment.\n" +
                "8.1. Termination. If we give written notice to you of a delay in occupancy when or after the Lease begins, you may termi- nate the Lease within 3 days after you receive written notice.\n" +
                "If we give you written notice before the date the Lease begins and the notice states that a construction or other delay is expected and that the apartment will be ready for you to occupy on a specifc date, you may terminate the Lease within 7 days after receiving written notice.\n" +
                "After proper termination, you are entitled only to refund of any deposit(s) and any Rent you paid.\n" +
                "      $H - I$ $AdultOcc - I1$ $e-Doc Signer - I$\n" +
                "Page 2 of 6\n" +
                "\n" +
                "9. Care of Unit and Damages. You must promptly pay or reimburse us for loss, damage, consequential damages, government fnes or charges, or cost of repairs or service in the apartment community because of a Lease or Community Policies violation; improper use, negligence, or other conduct by you, your invitees, your occupants, or your guests; or, as allowed by law, any other cause not due to our negligence or fault, except for damages by acts of God to the extent they couldn’t be mitigated by your action or inaction.\n" +
                "Unless damage or wastewater stoppage is due to our negligence, we’re not liable for—and you must pay for—repairs and replace- ments occurring during the Lease term or renewal period, includ- ing: (A) damage from wastewater stoppages caused by improper objects in lines exclusively serving your apartment; (B) damage to doors, windows, or screens; and (C) damage from windows or doors left open.\n" +
                "RESIDENT LIFE\n" +
                "10. Community Policies. Community Policies become part of the Lease and must be followed. We may make changes, including addi- tions, to our written Community Policies, and those changes can be- come efective immediately if the Community Policies are distributed and applicable to all units in the apartment community and do not change the dollar amounts in Lease Details.\n" +
                "10.1. Photo/Video Release. You give us permission to use any photograph, likeness, image or video taken of you while you are using property common areas or participating in any event sponsored by us.\n" +
                "10.2. Disclosure of Information. At our sole option, we may,\n" +
                "but are not obligated to, share and use information related\n" +
                "to this Lease for law-enforcement, governmental, or business purposes. At our request, you authorize any utility provider to give us information about pending or actual connections or disconnections of utility service to your apartment.\n" +
                "10.3. Guests. We may exclude from the apartment community any guests or others who, in our sole judgment, have been violating the law, violating this Lease or our Community Policies, or disturbing other residents, neighbors, visitors, or owner representatives. We may also exclude from any outside area or common area anyone who refuses to show photo identifcation or refuses to identify himself or herself as a resident, an authorized occupant, or a guest of a specifc resident in the community.\n" +
                "Anyone not listed in this Lease cannot stay in the apartment for more than ___7____ days in one week without our prior written consent, and no more than twice that many days in any one month. If the previous space isn’t flled in, 2 days total per week will be the limit.\n" +
                "10.4. Notice of Convictions and Registration. You must\n" +
                "notify us within 15 days if you or any of your occupants:\n" +
                "(A) are convicted of any felony, (B) are convicted of any misdemeanor involving a controlled substance, violence to another person, or destruction of property, or (C) register as a sex ofender. Informing us of a criminal conviction or sex-ofender registration doesn’t waive any rights we may have against you.\n" +
                "10.5. Odors and Noise. You agree that odors, smoke and smells including those related to cooking and everyday noises or sounds are all a normal part of a multifamily living environment and that it is impractical for us to prevent them from penetrating your apartment.\n" +
                "11. Conduct. You agree to communicate and conduct yourself in a law- ful, courteous and reasonable manner at all times when interacting with us, our representatives and other residents or occupants. Any acts of unlawful, discourteous or unreasonable communication or conduct by you, your occupants or guests is a breach of this Lease.\n" +
                "You must use customary diligence in maintaining the apartment, keeping it in a sanitary condition and not damaging or littering the common areas. Trash must be disposed of at least weekly. You will use your apartment and all other areas, including any balconies, with reasonable care. We may regulate the use of passageways, patios, balconies, porches, and activities in common areas.\n" +
                "11.1. Prohibited Conduct. You, your occupants, and your guests will not engage in unlawful, discourteous or unreasonable behavior including, but not limited to, any of the following activities:\n" +
                "(a) criminal conduct; manufacturing, delivering, or possessing a controlled substance or drug parapher- nalia; engaging in or threatening violence; possessing\n" +
                "a weapon prohibited by state law; discharging a frearm in the apartment community; or, except when\n" +
                "allowed by law, displaying or possessing a gun, knife,\n" +
                "or other weapon in the common area, or in a way that may alarm others;\n" +
                "(b) behavinginaloud,obnoxiousordangerousmanner;\n" +
                "Apartment Lease Contract ©2022, Texas Apartment Association, Inc.\n" +
                "(c) disturbing or threatening the rights, comfort, health, safety, or convenience of others, including us, our agents, or our representatives;\n" +
                "(d) disruptingourbusinessoperations;\n" +
                "(e) storing anything in closets containing water heaters or gas appliances;\n" +
                "(f) tampering with utilities or telecommunication equipment;\n" +
                "(g) bringinghazardousmaterialsintotheapartment community;\n" +
                "(h) usingwindowsforentryorexit;\n" +
                "(i) heating the apartment with gas-operated appliances;\n" +
                "(j) making bad-faith or false allegations against us or our agents to others;\n" +
                "(k) smoking of any kind, that is not in accordance with our Community Policies or Lease addenda;\n" +
                "(l) using glass containers in or near pools; or\n" +
                "(m) conducting any kind of business (including child-care services) in your apartment or in the apartment community—except for any lawful business\n" +
                "conducted “at home” by computer, mail, or telephone if customers, clients, patients, employees or other business associates do not come to your apartment\n" +
                "for business purposes.\n" +
                "12. Animals. No living creatures of any kind are allowed, even tempo- rarily, anywhere in the apartment or apartment community un- less we’ve given written permission. If we allow an animal, you must sign a separate Animal Addendum and, except as set forth in the ad- dendum, pay an animal deposit and applicable fees and additional monthly rent, as applicable. An animal deposit is considered a gener- al security deposit. You represent that any requests, statements and representations you make, including those for an assistance or sup- port animal, are true, accurate and made in good faith. Feeding stray, feral or wild animals is a breach of this Lease.\n" +
                "12.1. Removal of Unauthorized Animal. We may remove an unauthorized animal by (1) leaving, in a conspicuous place in the apartment, a written notice of our intent to remove the animal within 24 hours; and (2) following the procedures of Par. 14. We may: keep or kennel the animal; turn the animal over to a humane society, local authority or rescue organization; or return the animal to you if\n" +
                "we consent to your request to keep the animal and you have completed and signed an Animal Addendum and paid all fees. When keeping or kenneling an animal, we won’t be liable for loss, harm, sickness, or death of the animal unless due to our negligence. You must pay for the animal’s reasonable care and kenneling charges.\n" +
                "12.2. Violations of Animal Policies and Charges. If you or\n" +
                "any guest or occupant violates the animal restrictions of this Lease or our Community Policies, you’ll be subject to charges, damages, eviction, and other remedies provided in this Lease, including animal violation charges listed in Lease Details from the date the animal was brought into your apartment until it is removed. If an animal has been in the apartment at any time during your term of occupancy (with or without our consent), we’ll charge you for all cleaning and repair costs, including defeaing, deodorizing, and shampooing. Initial and daily animal-violation charges and animal-removal charges are liquidated damages for our time, inconvenience, and overhead in enforcing animal restrictions and Community Policies.\n" +
                "13. Parking. You may not be guaranteed parking. We may regulate the time, manner, and place of parking of all motorized vehicles and other modes of transportation, including bicycles and scooters, in our Community Policies. In addition to other rights we have to tow or boot vehicles under state law, we also have the right to remove, at the expense of the vehicle owner or operator, any vehicle that is not in compliance with our Community Policies.\n" +
                "14. When We May Enter. If you or any other resident, guest or occupant is present, then repair or service persons, contractors, law ofcers, government representatives, lenders, appraisers, prospective resi- dents or buyers, insurance agents, persons authorized to enter under your rental application, or our representatives may peacefully enter the apartment at reasonable times for reasonable business purposes. If nobody is in the apartment, then any such person may enter peace- fully and at reasonable times (by breaking a window or other means when necessary) for reasonable business purposes if written notice of the entry is left in a conspicuous place in the apartment immediately after the entry. We are under no obligation to enter only when you are present, and we may, but are not obligated to, give prior notice or make appointments.\n" +
                " $H - I$ $AdultOcc - I1$ $e-Doc Signer - I$\n" +
                "Page 3 of 6\n" +
                "\n" +
                "15. Requests, Repairs and Malfunctions.\n" +
                "15.1. Written Requests Required. If you or any occupant needs to send a request—for example, for repairs, installations, services, ownership disclosure, or security-related matters— it must be written and delivered to our designated representative in accordance with our Community Policies (except for fair-housing accommodation or modifcation requests or situations involving imminent danger or threats to health or safety, such as fre, smoke, gas, explosion, or crime in progress). Our written notes regarding your oral request do not constitute a written request from you. Our complying with or responding to any oral request doesn’t waive the strict requirement for written notices under this Lease. A request for maintenance or repair by anyone residing in your apartment constitutes a request from all residents. The time, manner, method and means of performing maintenance and repairs, including whether or which vendors to use,\n" +
                "are within our sole discretion.\n" +
                "15.2. Your Requirement to Notify. You must promptly notify us in writing of air conditioning or heating problems, water leaks or moisture, mold, electrical problems, malfunctioning lights, broken or missing locks or latches, or any other condition that poses a hazard or threat to property, health, or safety. Unless we instruct otherwise, you are required to keep the apartment cooled or heated according to our Community Policies. Air conditioning problems are normally not emergencies.\n" +
                "15.3. Utilities. We may change or install utility lines or equipment serving the apartment if the work is done reasonably without substantially increasing your\n" +
                "utility costs. We may turn of equipment and interrupt utilities as needed to perform work or to avoid property damage or other emergencies. If utilities malfunction or are damaged by fre, water, or similar cause, you must notify our representative immediately.\n" +
                "15.4. Your Remedies. We’ll act with customary diligence to make repairs and reconnections within a reasonable\n" +
                "time, taking into consideration when casualty-insurance proceeds are received. Unless required by statute after\n" +
                "a casualty loss, or during equipment repair, your Rent\n" +
                "will not abate in whole or in part. “Reasonable time” accounts for the severity and nature of the problem and\n" +
                "the reasonable availability of materials, labor, and\n" +
                "utilities. Ifwefailtotimelyrepairaconditionthat materially afects the physical health or safety of an ordinary resident as required by the Texas Property Code, you may be entitled to exercise remedies under § 92.056 and § 92.0561 of the Texas Property Code. If you follow the procedures under those sections, the following remedies, among others, may be available to you:\n" +
                "(1) termination of the Lease and an appropriate refund under 92.056(f); (2) have the condition repaired or remedied according to § 92.0561; (3) deduct from the Rent the cost of the repair or remedy according to § 92.0561; and 4) judicial remedies according to § 92.0563.\n" +
                "16. Our Right to Terminate for Apartment Community Damage or Closure. If, in our sole judgment, damages to the unit or building are signifcant or performance of needed repairs poses a danger to you, we may terminate this Lease and your right to possession by giving you at least 7 days’ written notice. If termination occurs, you agree we’ll refund only prorated rent and all deposits, minus lawful deduc- tions. We may remove your personal property if, in our sole judg- ment, it causes a health or safety hazard or impedes our ability to make repairs.\n" +
                "16.1. Property Closure. We also have the right to terminate this Lease and your right to possession by giving you at least 30 days’ written notice of termination if we are demolishing your apartment or closing it and it will no longer be used for residential purposes for at least 6 months, or if any part of the property becomes subject to an eminent domain proceeding.\n" +
                "17. Assignments and Subletting. You may not assign this Lease or sub- let your apartment. You agree that you won‘t rent, ofer to rent or license all or any part of your apartment to anyone else unless other- wise agreed to in advance by us in writing. You agree that you won‘t accept anything of value from anyone else for the use of any part of your apartment. You agree not to list any part of your apartment on any lodging or short-term rental website or with any person or ser- vice that advertises dwellings for rent.\n" +
                "18. Security and Safety Devices. We’ll pay for missing security de- vices that are required by law. You’ll pay for: (A) rekeying that you request (unless we failed to rekey after the previous resi- dent moved out); and (B) repairs or replacements because of misuse or damage by you or your family, your occupants, or your guests. You must pay immediately after the work is done unless state law authorizes advance payment. You must also pay in advance for any additional or changed security devices you request.\n" +
                "Apartment Lease Contract ©2022, Texas Apartment Association, Inc.\n" +
                "Texas Property Code secs. 92.151, 92.153, and 92.154 require, with some exceptions, that we provide at no cost to you when occupancy begins: (A) a window latch on each window; (B) a doorviewer (peep- hole or window) on each exterior door; (C) a pin lock on each sliding door; (D) either a door-handle latch or a security bar on each sliding door; (E) a keyless bolting device (deadbolt) on each exterior door; and (F) either a keyed doorknob lock or a keyed deadbolt lock on one entry door. Keyed locks will be rekeyed after the prior resident moves out. The rekeying will be done either before you move in or within 7 days after you move in, as required by law. If we fail to in- stall or rekey security devices as required by law, you have the right to do so and deduct the reasonable cost from your next Rent pay- ment under Texas Property Code sec. 92.165(1). We may deactivate or not install keyless bolting devices on your doors if (A) you or an occupant in the dwelling is over 55 or disabled, and (B) the require- ments of Texas Property Code sec. 92.153(e) or (f) are satisfed.\n" +
                "18.1. Smoke Alarms and Detection Devices. We’ll furnish smoke alarms or other detection devices required by law or city ordinance. We may install additional detectors not so required. We’ll test them and provide working batteries when you frst take possession of your apartment. Upon request, we’ll provide, as required by law, a smoke alarm capable of alerting a person with a hearing impairment.\n" +
                "You must pay for and replace batteries as needed,\n" +
                "unless the law provides otherwise. We may replace dead or missing batteries at your expense, without prior notice to you. Neither you nor your guests or occupants may disable alarms or detectors. If you damage or disable the smoke alarm or remove a battery without replacing it with a working battery, you may be liable to us under Texas Property Code sec. 92.2611 for $100 plus one month’s Rent, actual damages, and attorney’s fees.\n" +
                "18.2. Duty to Report. You must immediately report to us any missing, malfunctioning or defective security devices, smoke alarms or detectors. You’ll be liable if you fail to report malfunctions, or fail to report any loss, damage, or fnes resulting from fre, smoke, or water.\n" +
                "19. Resident Safety and Loss. Unless otherwise required by law, none of us, our employees, agents, or management companies are liable to you, your guests or occupants for any damage, personal injury, loss to personal property, or loss of business or personal income, from any cause, including but not limited to: negligent or intention- alactsofresidents,occupants,orguests;theft,burglary,assault, vandalism or other crimes; fre, food, water leaks, rain, hail, ice, snow, smoke, lightning, wind, explosions, interruption of utilities, pipe leaks or other occurrences unless such damage, injury or loss is caused exclusively by our negligence.\n" +
                "We do not warrant security of any kind. You agree that you will not rely upon any security measures taken by us for personal security, and that you will call 911 and local law enforcement authorities if any security needs arise.\n" +
                "You acknowledge that we are not equipped or trained to provide personal security services to you, your guests or occupants. You rec- ognize that we are not required to provide any private security ser- vices and that no security devices or measures on the property are fail-safe. You further acknowledge that, even if an alarm or gate ame- nities are provided, they are mechanical devices that can malfunc- tion. Any charges resulting from the use of an intrusion alarm will be charged to you, including, but not limited to, any false alarms with police/fre/ambulance response or other required city charges.\n" +
                "20. Condition of the Premises and Alterations.\n" +
                "20.1. As-Is. We disclaim all implied warranties. You accept the apartment, fxtures, and furniture as is, except for conditions materially afecting the health or safety of ordinary persons. You’ll be given an Inventory and Condition Form at or before move-in. You agree that\n" +
                "after completion of the form or within 48 hours after move-in, whichever comes frst, you must note on the form all defects or damage, sign the form, return it to us, and the form accurately refects the condition of the premises for purposes of determining any refund due to you when you move out. Otherwise, everything will be considered to be in a clean, safe, and good working condition. You must still send a separate request for any repairs needed as provided by Par. 15.1.\n" +
                "20.2. Standards and Improvements. Unless authorized by law or by us in writing, you must not perform any repairs, painting, wallpapering, carpeting, electrical changes, or otherwise alter our property. No holes or stickers are allowed inside or outside the apartment. Unless our Community Policies state otherwise, we’ll permit a reasonable number of small nail holes for hanging pictures on sheetrock walls and in grooves of wood- paneled walls. No water furniture, washing machines, dryers, extra phone or television outlets, alarm systems,\n" +
                "     $H - I$ $AdultOcc - I1$ $e-Doc Signer - I$\n" +
                "Page 4 of 6\n" +
                "\n" +
                "cameras, video or other doorbells, or lock changes,\n" +
                "additions, or rekeying is permitted unless required by law\n" +
                "or we’ve consented in writing. You may install a satellite\n" +
                "dish or antenna, but only if you sign our satellite-dish or antenna lease addendum, which complies with reasonable restrictions allowed by federal law. You must not alter, damage, or remove our property, including alarm systems, detection devices, appliances, furniture, telephone and television wiring, screens, locks, or security devices. When you move in, we’ll supply light bulbs for fxtures we furnish, including exterior fxtures operated from inside the apartment; after that, you’ll replace them at your expense with bulbs of the same type and wattage. Your improvements to the apartment (made with or without our consent) become ours unless we agree otherwise in writing.\n" +
                "21. Notices. Written notice to or from our employees, agents, or management companies constitutes notice to or from us. Notices to you or any other resident of the apartment constitute notice to all residents. Notices and requests from any resident constitute notice from all residents. Only residents can give notice of Lease termination and intent to move out under Par. 7.3. All notices and documents will be in English and, at our option, in any other language that you read or speak.\n" +
                "21.1. Electronic Notice. Notice may be given electronically by us to you if allowed by law. If allowed by law and in accordance with our Community Policies, electronic notice from you to us must be sent to the email address and/or portal specifed in Community Policies. Notice may also be given by phone call or to a physical address if allowed in our Community Policies.\n" +
                "You represent that you have provided your current email address to us, and that you will notify us in the event your email address changes.\n" +
                "EVICTIONANDREMEDIES\n" +
                "22. Liability.EachresidentisjointlyandseverallyliableforallLease obligations. If you or any guest or occupant violates the Lease or our Community Policies, all residents are considered to have violated the Lease.\n" +
                "22.1. Indemnification by You. You’ll defend, indemnify and hold us and our employees, agents, and management company harmless from all liability arising from your conduct or requests to our representatives and from the conduct of or requests by your invitees, occupants or guests.\n" +
                "23. DefaultbyResident.\n" +
                "23.1. Acts of Default. You’ll be in default if: (A) you don’t timely pay Rent, including monthly recurring charges, or other amounts you owe; (B) you or any guest or occupant violates this Lease, our Community Policies,\n" +
                "or fre, safety, health, criminal or other laws, regardless of whether or where arrest or conviction occurs; (C) you give incorrect, incomplete, or false answers in a rental application or in this Lease; or (D) you or any occupant is charged, detained, convicted, or given deferred adjudication or pretrial diversion for (1) an ofense involving actual or potential physical harm to a person, or involving the manufacture or delivery of a controlled substance, marijuana, or drug paraphernalia as defned in the Texas Controlled Substances Act, or (2) any sex- related crime, including a misdemeanor.\n" +
                "23.2. Eviction. If you default, including holding over, we may end your right of occupancy by giving you at least a 24- hour written notice to vacate. Termination of your possession rights doesn’t release you from liability for future Rent or other Lease obligations. After giving notice to vacate or fling an eviction suit, we may still accept Rent or other sums due; the fling or acceptance doesn’t waive or diminish our right of eviction or any other contractual or statutory right. Accepting money at any time doesn’t waive our right to damages, to past or future Rent or other sums,\n" +
                "or to our continuing with eviction proceedings. In an eviction, Rent is owed for the full rental period and will not be prorated.\n" +
                "23.3. Acceleration. Unless we elect not to accelerate Rent, all monthly Rent for the rest of the Lease term or renewal period will be accelerated automatically without notice or demand (before or after acceleration) and will be immediately due if, without our written consent: (A) you move out, remove property in preparing to move out,\n" +
                "or you or any occupant gives oral or written notice of intent to move out before the Lease term or renewal period ends; and (B) you haven’t paid all Rent for the entire Lease term or renewal period. Remaining Rent will also be accelerated if you’re judicially evicted or move out when we demand because you’ve defaulted.\n" +
                "Apartment Lease Contract ©2022, Texas Apartment Association, Inc.\n" +
                "If you don’t pay the frst month’s Rent when or before the Lease begins, all future Rent for the Lease term will be automatically accelerated without notice and become immediately due. We also may end your right of occupancy and recover damages, future Rent, attorney’s fees, court costs, and other lawful charges.\n" +
                "23.4. Holdover. You or any occupant or guest must not hold over beyond the date contained in: (1) your move-out notice, (2) our notice to vacate, (3) our notice of non- renewal, or (4) a written agreement specifying a diferent move-out date. If a holdover occurs, then you’ll be\n" +
                "liable to us for all Rent for the full term of the previously signed lease of a new resident who can’t occupy because of the holdover, and at our option, we may extend the Lease term and/or increase the Rent by 25% by delivering written notice to you or your apartment while you continue to hold over.\n" +
                "23.5. Other Remedies. We may report unpaid amounts to\n" +
                "credit agencies as allowed by law. If we or our debt\n" +
                "collector tries to collect any money you owe us, you\n" +
                "agree that we or the debt collector may contact you by\n" +
                "any legal means. If you default, you will pay us, in addition\n" +
                "to other sums due, any rental discounts or concessions agreed to in writing that have been applied to your account. We may recover attorney’s fees in connection with enforcing our rights under this Lease. All unpaid amounts you owe bear interest at the rate provided by Texas Finance Code Section 304.003(c) from the due date. You must pay all collection- agency fees if you fail to pay sums due within 10 days after you are mailed a letter demanding payment and stating that collection-agency fees will be added if you don’t pay all sums by that deadline. You are also liable for a charge (not to exceed $150) to cover our time, cost and expense for any eviction proceeding against you, plus our attorney’s fees and expenses, court costs, and fling fees actually paid.\n" +
                "24. Representatives’AuthorityandWaivers.Ourrepresentatives(in- cluding management personnel, employees, and agents) have no authority to waive, amend, or terminate this Lease or any part of it unless in writing and signed, and no authority to make promises, rep- resentations, or agreements that impose security duties or other ob- ligations on us or our representatives, unless in writing and signed. No action or omission by us will be considered a waiver of our rights or of any subsequent violation, default, or time or place of performance. Our choice to enforce, not enforce or delay enforcement of written-no- tice requirements, rental due dates, acceleration, liens, or any other rights isn’t a waiver under any circumstances. Delay in demanding sums you owe is not a waiver. Except when notice or demand is required by law, you waive any notice and demand for performance from us if you default. Nothing in this Lease constitutes a waiver of our remedies for a breach under your prior lease that occurred before the Lease term begins.\n" +
                "All remedies are cumulative. Exercising one remedy won’t constitute an election or waiver of other remedies. All provisions regarding our nonliability or nonduty apply to our employees, agents, and manage- ment companies. No employee, agent, or management company is personally liable for any of our contractual, statutory, or other obliga- tions merely by virtue of acting on our behalf.\n" +
                "END OF THE LEASE TERM\n" +
                "25. Move-OutNotice. Beforemovingout,youmustgiveourrepresen- tative advance written move-out notice as stated in Par. 4, even if the Lease has become a month-to-month lease. The move-out date can’t be changed unless we and you both agree in writing.\n" +
                "Your move-out notice must comply with each of the following:\n" +
                "(a) Unless we require more than 30 days’ notice, if you give notice on the frst day of the month you intend to move out, move out will be on the last day of that month.\n" +
                "(b) Your move-out notice must not terminate the Lease before the end of the Lease term or renewal period.\n" +
                "(c) If we require you to give us more than 30 days’ written notice to move out before the end of the Lease term, we will give you 1 written reminder not less than 5 days nor more than 90 days before your deadline for giving us your written move-out notice. If we fail to give a reminder notice, 30 days’ written notice to move out is required.\n" +
                "(d) You must get from us a written acknowledgment of your notice.\n" +
                "26. Move-OutProcedures.\n" +
                "26.1. Cleaning. You must thoroughly clean the apartment, including doors, windows, furniture, bathrooms, kitchen appliances, patios, balconies, garages, carports, and storage rooms. You must follow move-out cleaning instructions if they have been provided. If you don’t clean adequately, you’ll be liable for reasonable cleaning charges—including charges for cleaning carpets, draperies, furniture, walls, etc. that are soiled beyond normal wear (that is, wear or soiling that occurs without negligence, carelessness, accident, or abuse).\n" +
                "  $H - I$ $AdultOcc - I1$ $e-Doc Signer - I$\n" +
                "Page 5 of 6\n" +
                "\n" +
                "26.2. Move-Out Inspection. We may, but are not obligated to, provide a joint move-out inspection. Our representatives have no authority to bind or limit us regarding deductions for repairs, damages, or charges. Any statements or estimates by us or our representative are subject to our correction, modi- fcation, or disapproval before fnal accounting or refunding.\n" +
                "27. Surrender and Abandonment. You have surrendered the apartment when: (A) the move-out date has passed and no one is living in the apartment in our reasonable judgment; or (B) apartment keys and ac- cess devices listed in Par. 2.1 have been turned in to us—whichever happens frst.\n" +
                "You have abandoned the apartment when all of the following have occurred: (A) everyone appears to have moved out in our reasonable judgment; (B) you’ve been in default for nonpayment of Rent for 5 consecutive days, or water, gas, or electric service for the apartment not connected in our name has been terminated or transferred; and (C) you’ve not responded for 2 days to our notice left on the inside of the main entry door stating that we consider the apartment aban- doned. An apartment is also considered abandoned 10 days after the death of a sole resident.\n" +
                "27.1. The Ending of Your Rights. Surrender, abandonment, or judicial eviction ends your right of possession for all purposes and gives us the immediate right to clean up, make repairs in, and relet the apartment; determine any security-deposit deductions; and remove or store property left in the apartment.\n" +
                "27.2. Removal and Storage of Property. We, or law ofcers, may— but have no duty to—remove or store all property that in our sole judgment belongs to you and remains in the apartment or in common areas (including any vehicles you or any occupant or guest owns or uses) after you’re judicially evicted or if you surrender or abandon the apartment.\n" +
                "We’re not liable for casualty, loss, damage, or theft. You must pay reasonable charges for our packing, removing and storing any property.\n" +
                "Except for animals, we may throw away or give to a charitable organization all personal property that is:\n" +
                "(1) leftintheapartmentaftersurrenderorabandonment;or (2) left outside more than 1 hour after writ of possession is\n" +
                "executed, following judicial eviction.\n" +
                "An animal removed after surrender, abandonment, or eviction may be kenneled or turned over to a local authority, humane society, or rescue organization.\n" +
                "GENERAL PROVISIONS AND SIGNATURES\n" +
                "28. TAAMembership.We,themanagementcompanyrepresentingus, or any locator service that you used confrms membership in good standing of both the Texas Apartment Association and the afliated local apartment association for the area where the apartment is located at the time of signing this Lease. If not, the following applies: (A) this Lease is voidable at your option and is unenforceable by us (except for property damages); and (B) we may not recover past or future rent or other charges. The above remedies also apply if both of the following occur: (1) the Lease is automatically renewed on a month-to-month basis more than once after membership in TAA and the local association has lapsed; and (2) neither the owner nor the man- agement company is a member of TAA and the local association during the third automatic renewal. A signed afdavit from the afliated local apartment association attesting to nonmembership when the Lease\n" +
                "or renewal was signed will be conclusive evidence of nonmembership. Governmental entities may use TAA forms if TAA agrees in writing.\n" +
                "Name, address and telephone number of locator service (if applicable): ________________________________________________________ ________________________________________________________ ________________________________________________________ _________________________________________________________\n" +
                "29. Severability and Survivability. If any provision of this Lease is invalid or unenforceable under applicable law, it won’t invalidate the remain- der of the Lease or change the intent of the parties. Paragraphs 10.1, 10.2, 16, 27 and 31 shall survive the termination of this Lease. This Lease binds subsequent owners.\n" +
                "30. Controlling Law. Texas law governs this Lease. All litigation arising under this Lease and all Lease obligations must be brought in the county, and precinct if applicable, where the apartment is located.\n" +
                "31. Waivers. By signing this Lease, you agree to the following:\n" +
                "31.1. Class Action Waiver. You agree that you will not participate in any class action claims against us or our employees, agents, or management company. You must fle any claim against us individually, and you expressly waive your right to bring, represent, join or otherwise maintain a class action, collective action or similar proceeding against us in\n" +
                "any forum.\n" +
                "Apartment Lease Contract, TAA Ofcial Statewide Form 22-A/B-1/B-2 Revised February 2022\n" +
                "YOU UNDERSTAND THAT, WITHOUT THIS WAIVER, YOU COULD BE A PARTY IN A CLASS ACTION LAWSUIT. BY SIGNING THIS LEASE, YOU ACCEPT THIS WAIVER AND CHOOSE TO HAVE ANY CLAIMS DECIDED INDIVIDUALLY. THE PROVISIONS OF THIS PARAGRAPH SHALL SURVIVE THE TERMINATION OR EXPIRATION OF THIS LEASE.\n" +
                "31.2. Force Majeure. If we are prevented from completing substan- tial performance of any obligation under this Lease by occurrences that are beyond our control, including but\n" +
                "not limited to, an act of God, strikes, epidemics, war, acts of terrorism, riots, food, fre, hurricane, tornado, sabotage or governmental regulation, then we shall be excused from any further performance of obligations to the fullest extent allowed by law.\n" +
                "32. Special Provisions. The following, or attached Special Provisions and any addenda or Community Policies provided to you, are part of this Lease and supersede any conficting provisions in this Lease.\n" +
                "_________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________ _________________________________________________________\n" +
                "Before submitting a rental application or signing this Lease, you should review the documents and may consult an attorney. You are bound by this Lease when it is signed. An electronic signature is binding. This Lease is the entire agreement between you\n" +
                "and us. You are NOT relying on any oral representations.\n" +
                "Resident or Residents (all sign below) _____________________________________________________________\n" +
                "(Name of Resident) Date signed\n" +
                "_____________________________________________________________ (Name of Resident) Date signed\n" +
                "_____________________________________________________________ (Name of Resident) Date signed\n" +
                "_____________________________________________________________ (Name of Resident) Date signed\n" +
                "_____________________________________________________________ (Name of Resident) Date signed\n" +
                "_____________________________________________________________ (Name of Resident) Date signed\n" +
                "Owner or Owner’s Representative (signing on behalf of owner) _____________________________________________________________\n" +
                "       $H - S$\n" +
                "$AdultOcc - S1$\n" +
                "$e-Doc Signer - S$\n" +
                "Page 6 of 6\n";


        System.out.println("Text Length::::::::" + text.toCharArray().length);


        List<TextSegment> chunks = DocumentSplitters
                .recursive(500, 0)
                .split(Document.document(text));

        List<InsertParam.Field> fields = new ArrayList<>();
        EmbeddingModel embeddingModel = new E5SmallV2EmbeddingModel();
        Embedding embedding = null;
        for (int i = 0; i < chunks.size(); i++) {
            TextSegment textSegment = TextSegment.from(chunks.get(i).text());

            embedding = embeddingModel
                    .embed(textSegment)
                    .content();

            doc_text_vector_array.add(embedding.vectorAsList());
            doc_id_array.add((long) i);
            char_count_array.add((long) textSegment.text().length());
            doc_text_array.add(textSegment.text());

            fields.add(new InsertParam.Field(bookIdField.getName(), doc_id_array));
            fields.add(new InsertParam.Field(wordCountField.getName(), char_count_array));
            fields.add(new InsertParam.Field(bookIntroField.getName(), doc_text_vector_array));
            fields.add(new InsertParam.Field(docText.getName(), doc_text_array));
        }
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
        R<MutationResult> insertR = milvusClient.insert(insertParam);
        System.out.println("Successfully Populated");

        // flush data
        System.out.println("Flushing...");

        milvusClient.flush(FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(collectionName))
                .withSyncFlush(true)
                .withSyncFlushWaitingInterval(50L)
                .withSyncFlushWaitingTimeout(30L)
                .build());

        System.out.println("Flushed");

//         build index
        System.out.println("Building AutoIndex...");
        final IndexType INDEX_TYPE = IndexType.AUTOINDEX;   // IndexType
        createIndex(milvusClient, collectionName, bookIntroField, INDEX_TYPE);

        // load collection
        System.out.println("Loading collection...");
        long startLoadTime = System.currentTimeMillis();
        milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(true)
                .withSyncLoadWaitingInterval(500L)
                .withSyncLoadWaitingTimeout(100L)
                .build());

        // search
        final Integer SEARCH_K = 3;                       // TopK
        final String SEARCH_PARAM = "{\"nprobe\":10}";    // Params
        List<String> search_output_fields = Arrays.asList("doc_id", "char_count", "doc_text");

        Embedding embeddingQuery = embeddingModel.embed("Residents name").content();

        searchData(milvusClient, collectionName, bookIntroField, SEARCH_K, SEARCH_PARAM, search_output_fields, embedding.vectorAsList());

        milvusClient.close();
    }

    private static void createIndex(MilvusServiceClient milvusClient, String collectionName, FieldType bookIntroField, IndexType INDEX_TYPE) {
        R<RpcStatus> indexR = milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName(bookIntroField.getName())
                        .withIndexType(INDEX_TYPE)
                        .withMetricType(MetricType.COSINE)
                        .withSyncMode(Boolean.TRUE)
                        .withSyncWaitingInterval(500L)
                        .withSyncWaitingTimeout(30L)
                        .build());
    }

    private static void searchData(MilvusServiceClient milvusClient, String collectionName, FieldType bookIntroField, Integer SEARCH_K, String SEARCH_PARAM, List<String> search_output_fields, List<Float> floatList) {
        List<List<Float>> search_vectors = Collections.singletonList(floatList);
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withOutFields(search_output_fields)
                .withTopK(SEARCH_K)
                .withVectors(search_vectors)
                .withVectorFieldName(bookIntroField.getName())
                .withParams(SEARCH_PARAM)
                .build();

        R<SearchResults> search = milvusClient.search(searchParam);

        System.out.println("Searching vector: " + search_vectors);
//        System.out.println("Result: " + search.getData().getResults().getFieldsDataList());

        SearchResultsWrapper wrapperSearch = new SearchResultsWrapper(search.getData().getResults());

        System.out.println("{ "+"doc_id" + wrapperSearch.getFieldWrapper("doc_id").getFieldData()+"}");

//        System.out.println("Result: " + search.getData().getResults());
    }

}
