/*

################################################################################

Migration script to create the table

   Publication
   Author (Abstraction)
   Publication_Authors

Add column publication_id to the table Study

author: Cinzia Malangone
date:    11 Sept 2017
version: 2.2.0.039
################################################################################
*/

--------------------------------------------------------
--  CREATE TABLE, FOREIGN KEY AUTHOR
--------------------------------------------------------
  CREATE TABLE "AUTHOR" (
     "ID" NUMBER(19,0),
     "FULLNAME" VARCHAR2(255 CHAR) NOT NULL,
     "ORCID" VARCHAR2(255 CHAR),
     "CREATED" TIMESTAMP,
     "UPDATED" TIMESTAMP
     );

  ALTER TABLE "AUTHOR" ADD PRIMARY KEY ("ID") ENABLE;

--------------------------------------------------------
--  CREATE TABLE, FOREIGN KEY PUBLICATION
--------------------------------------------------------
  CREATE TABLE "PUBLICATION" (
     "ID" NUMBER(19,0),
     "PUBMED_ID" VARCHAR2(255 CHAR) NOT NULL,
     "PUBLICATION" VARCHAR2(255 CHAR),
     "TITLE" VARCHAR2(4000 BYTE),
     "PUBLICATION_DATE" DATE,
     "LIST_AUTHORS" VARCHAR2(4000 BYTE),
     "CREATED" TIMESTAMP,
     "UPDATED" TIMESTAMP
     );

  ALTER TABLE "PUBLICATION" ADD PRIMARY KEY ("ID") ENABLE;

  CREATE SEQUENCE PUBLICATION_TYPE_SEQ
     START WITH 1
     INCREMENT BY 1
     CACHE 1000;


--------------------------------------------------------
-- Create temporary trigger on PUBLICATION
--------------------------------------------------------
CREATE OR REPLACE TRIGGER PUBLICATION_TRG
BEFORE INSERT ON PUBLICATION
FOR EACH ROW
    BEGIN
        IF :NEW.ID IS NULL THEN
            SELECT PUBLICATION_TYPE_SEQ.NEXTVAL INTO :NEW.ID FROM DUAL;
        END IF;
    END;
/
ALTER TRIGGER PUBLICATION_TRG ENABLE;

--------------------------------------------------------
--  CREATE TABLE, FOREIGN KEY PUBLICATION_AUTHORS
--------------------------------------------------------
 CREATE TABLE "PUBLICATION_AUTHORS" (
      "PUBLICATION_ID" NUMBER(19,0) NOT NULL,
      "AUTHOR_ID" NUMBER(19,0) NOT NULL);


--------------------------------------------------------
--  Ref Constraints for Table PUBLICATION_AUTHORS
--------------------------------------------------------
  ALTER TABLE "PUBLICATION_AUTHORS" ADD CONSTRAINT "PUBLICATION_AUTH_ID_FK" FOREIGN KEY ("PUBLICATION_ID")
	  REFERENCES "PUBLICATION" ("ID") ENABLE;
  ALTER TABLE "PUBLICATION_AUTHORS" ADD CONSTRAINT "AUTHOR_PUBLICATION_ID_FK" FOREIGN KEY ("AUTHOR_ID")
	  REFERENCES "AUTHOR" ("ID") ENABLE;


--------------------------------------------------------
--  Add publication_id to study (foreigner key)
--------------------------------------------------------

ALTER TABLE "STUDY" ADD "PUBLICATION_ID" NUMBER(19,0);

--------------------------------------------------------
--  Ref Constraints for Table Publication
--------------------------------------------------------
  ALTER TABLE "STUDY" ADD CONSTRAINT "STUDY_PUBLICATION_ID_FK" FOREIGN KEY ("PUBLICATION_ID")
	  REFERENCES "PUBLICATION" ("ID") ENABLE;

--
-- INSERT INTO PUBLICATION(ID,PUBMED_ID) SELECT DISTINCT PUBLICATION_TYPE_SEQ.nextval, PUBMED_ID FROM STUDY;
--
