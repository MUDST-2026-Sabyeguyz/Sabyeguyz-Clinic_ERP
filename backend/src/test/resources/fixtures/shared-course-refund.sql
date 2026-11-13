-- Synthetic fixture works on V1023 and after V1024; no local database copy is used.
DO $fixture$
DECLARE
    actor bigint; branch bigint; patient bigint; seller bigint; template bigint;
    scheme bigint; permission bigint; method bigint; label bigint;
    original_receipt bigint; collection_receipt bigint; sale bigint; purchased_course bigint;
    original_payment bigint; cancellation bigint; legacy_refund bigint;
BEGIN
    SELECT user_id INTO STRICT actor FROM app_user WHERE username='manager';
    SELECT branch_id INTO STRICT branch FROM branch WHERE branch_code='01';
    SELECT patient_id INTO STRICT patient FROM patient ORDER BY patient_id LIMIT 1;
    SELECT staff_id INTO STRICT seller FROM staff WHERE staff_code='THP-001';
    SELECT service_id INTO STRICT template FROM service WHERE service_code='COURSE_10';
    SELECT commission_scheme_id INTO STRICT scheme FROM commission_scheme ORDER BY commission_scheme_id LIMIT 1;
    SELECT permission_id INTO STRICT permission FROM permission WHERE permission_name='SALE_CANCEL';
    SELECT payment_method_id INTO STRICT method FROM payment_method WHERE method_name='PromptPay';
    SELECT payment_label_id INTO STRICT label FROM payment_label WHERE payment_label_code='FULL';

    INSERT INTO receipt(receipt_no,patient_id,branch_id,sale_date,receipt_create_at,receipt_create_by,
        subtotal_before_discount,bill_discount_amount,net_amount,taxable_amount,tax_amount,
        receipt_status,payment_label,payment_label_id)
    VALUES('RE26010001',patient,branch,TIMESTAMP '2026-01-01',CURRENT_TIMESTAMP,actor,
        10000,0,10000,10000,0,'Issued','FullyPaid',label)
    RETURNING receipt_id INTO original_receipt;

    INSERT INTO sales_transaction(receipt_id,line_no,patient_id,branch_id,service_id,transaction_type,
        salesperson_staff_id,quantity,unit_price,amount,line_discount_amount,allocated_bill_discount_amount,
        commissionable_amount,item_name_snapshot,unit_name_snapshot,transaction_date,transaction_status,
        transaction_create_at,transaction_create_by)
    VALUES(original_receipt,1,patient,branch,template,'CoursePurchase',seller,1,10000,10000,0,0,10000,
        'Shared refund fixture course','course',CURRENT_TIMESTAMP,true,CURRENT_TIMESTAMP,actor)
    RETURNING transaction_id INTO sale;

    INSERT INTO course(branch_id,service_id,course_no,course_sequence,course_name,course_price,
        total_visits,bonus_visits,validity_value,validity_unit,expiry_date,course_status,
        course_create_at,course_create_by,receipt_id,purchaser_patient_id,sale_transaction_id,
        sale_month,seller_staff_id,commission_status,commission_scheme_id,commission_scheme_version,
        provisional_commission_rate,visits_used,remaining_visits,owner_net_commission_released_total,
        substitute_treatment_fee_total,treatment_fee_base_amount)
    VALUES(branch,template,'RE26010001',1,'Shared refund fixture course',10000,10,0,365,'Day',
        DATE '2099-12-31','Active',CURRENT_TIMESTAMP,actor,original_receipt,patient,sale,
        '2026-01',seller,'Provisional',scheme,1,0.05,0,10,0,0,10000)
    RETURNING course_id INTO purchased_course;

    INSERT INTO course_shared_member(course_id,patient_id,member_role)
    VALUES(purchased_course,patient,'Owner');
    INSERT INTO course_entitlement(course_id,patient_id,allocated_visits,used_visits,remaining_visits,updated_at)
    VALUES(purchased_course,patient,10,0,10,CURRENT_TIMESTAMP);
    INSERT INTO course_ledger(course_id,patient_id,entry_type,qty_change,balance_after,
        sales_transaction_id,ledger_create_at,ledger_create_by,event_at)
    VALUES(purchased_course,patient,'Purchase',10,10,sale,CURRENT_TIMESTAMP,actor,CURRENT_TIMESTAMP);
    INSERT INTO payment_transaction(patient_id,branch_id,amount,payment_method_id,payment_date,
        payment_status,payment_create_at,payment_create_by,receipt_id)
    VALUES(patient,branch,10000,method,CURRENT_TIMESTAMP,true,CURRENT_TIMESTAMP,actor,original_receipt)
    RETURNING payment_id INTO original_payment;

    INSERT INTO receipt(receipt_no,patient_id,branch_id,sale_date,receipt_create_at,receipt_create_by,
        subtotal_before_discount,bill_discount_type,bill_discount_value,bill_discount_amount,
        net_amount,taxable_amount,tax_amount,receipt_status,payment_label,payment_label_id)
    VALUES('RE26010002',patient,branch,TIMESTAMP '2026-01-02',CURRENT_TIMESTAMP,actor,
        1000,'Fixed',100,100,900,900,0,'Issued','FullyPaid',label)
    RETURNING receipt_id INTO collection_receipt;
    INSERT INTO receipt_course_payment(receipt_id,course_id,item_name_snapshot,amount,allocated_bill_discount_amount)
    VALUES(collection_receipt,purchased_course,'Shared refund fixture course',1000,100);
    INSERT INTO payment_transaction(patient_id,branch_id,amount,payment_method_id,payment_date,
        payment_status,payment_create_at,payment_create_by,receipt_id)
    VALUES(patient,branch,900,method,CURRENT_TIMESTAMP,true,CURRENT_TIMESTAMP,actor,collection_receipt);

    INSERT INTO transaction_cancellation(permission_id,payment_id,reason,cancelled_by,cancelled_at,
        cancellation_type,reason_code)
    VALUES(permission,original_payment,'Synthetic legacy refund item',actor,CURRENT_TIMESTAMP,'Refund','Other')
    RETURNING cancellation_id INTO cancellation;
    INSERT INTO refund_transaction(cancellation_id,original_payment_id,refund_amount,payment_method_id,
        refund_status,requested_at,requested_by)
    VALUES(cancellation,original_payment,1,method,'Pending',CURRENT_TIMESTAMP,actor)
    RETURNING refund_id INTO legacy_refund;
    INSERT INTO course_refund_item(refund_id,course_id,refund_amount,cancelled_visits)
    VALUES(legacy_refund,purchased_course,1,1);
END;
$fixture$;
