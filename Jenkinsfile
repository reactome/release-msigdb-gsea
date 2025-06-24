import org.reactome.release.jenkins.utilities.Utilities

def utils = new Utilities()

pipeline {
	agent any

	stages {
		stage('Main: Run MSigDB-GSEA exporter') {
			steps {
				script {
					withCredentials([file(credentialsId: 'Config', variable: 'CONFIG_FILE')]) {
						writeFile file: 'config.properties', text: readFile(CONFIG_FILE)
						sh "mvn clean package"
						sh "java -jar target/msigdb-gsea-exporter-*-jar-with-dependencies.jar -c config.properties"
						sh "rm config.properties"
					}
				}
			}
		}

		stage('Post: Archive Outputs'){
			steps {
				script {
					def currentRelease = utils.getReleaseVersion()
					def s3Path = "${env.S3_RELEASE_DIRECTORY_URL}/${currentRelease}/msigdb-gsea"
					sh "mkdir -p data/"
					sh "mv Reactome_GeneSet_${currentRelease}.txt data/"
					sh "find data/ -type f ! -name \"*.gz\" -exec gzip -f {} ';'"
					sh "find logs/ -type f ! -name \"*.gz\" -exec gzip -f {} ';'"
					sh "aws s3 --no-progress --recursive cp logs/ $s3Path/logs/"
					sh "aws s3 --no-progress --recursive cp data/ $s3Path/data/"
					sh "rm -r logs data"
				}
			}
		}
	}
}
